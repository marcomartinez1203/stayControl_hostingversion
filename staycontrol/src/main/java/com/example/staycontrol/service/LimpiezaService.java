package com.example.staycontrol.service;

import com.example.staycontrol.dto.*;
import com.example.staycontrol.exception.ApiException;
import com.example.staycontrol.model.*;
import com.example.staycontrol.repository.LimpiezaRepository;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDate;

@Service
public class LimpiezaService {
    public record Creacion(LimpiezaResponse tarea, boolean repetida) { }
    private final LimpiezaRepository repository;
    private final IdempotencyService idempotencia;
    private final TransactionTemplate transacciones;

    public LimpiezaService(LimpiezaRepository repository, IdempotencyService idempotencia, MongoTransactionManager manager) {
        this.repository = repository;
        this.idempotencia = idempotencia;
        this.transacciones = new TransactionTemplate(manager);
    }

    public Creacion crear(String clave, LimpiezaRequest request) {
        String id = idempotencia.identificador("POST:/api/v1/limpieza", clave);
        if (request == null) { throw ApiException.invalida("El cuerpo JSON es obligatorio."); }
        DatosLimpieza datos = request.validarYNormalizar();
        var anterior = repository.buscar(id);
        if (anterior.isPresent()) { return repetir(anterior.get(), datos); }
        repository.prepararIndices();
        try {
            return transacciones.execute(status -> {
                var existente = repository.buscar(id);
                if (existente.isPresent()) { return repetir(existente.get(), datos); }
                if (!repository.vincularHabitacion(datos.habitacionId())) {
                    throw new ApiException(HttpStatus.NOT_FOUND, "HABITACION_NO_ENCONTRADA", "Habitación no encontrada.");
                }
                var nueva = repository.insertar(new TareaLimpieza(id, datos, EstadoLimpieza.PENDIENTE));
                return new Creacion(respuesta(nueva), false);
            });
        } catch (DuplicateKeyException exception) {
            return repository.buscar(id).map(tarea -> repetir(tarea, datos))
                .orElseThrow(() -> new DataAccessResourceFailureException("Reintenta con la misma clave."));
        }
    }

    public LimpiezaResponse obtener(String id) { return respuesta(buscar(id)); }

    public PaginaResponse<LimpiezaResponse> listar(EstadoLimpieza estado, String habitacionId,
        LocalDate fecha, int pagina, int tamano) {
        if (habitacionId != null) { validarId(habitacionId); }
        if (fecha != null && (fecha.getYear() < 2000 || fecha.getYear() > 2100)) {
            throw ApiException.invalida("fecha debe estar entre 2000 y 2100.");
        }
        if (pagina < 0 || pagina > 10000 || tamano < 1 || tamano > 100) {
            throw ApiException.invalida("pagina debe estar entre 0 y 10000; tamano, entre 1 y 100.");
        }
        String dia = fecha == null ? null : fecha.toString();
        return new PaginaResponse<>(repository.listar(estado, habitacionId, dia, pagina, tamano).stream()
            .map(this::respuesta).toList(), pagina, tamano, repository.contar(estado, habitacionId, dia));
    }

    public LimpiezaResponse cambiarEstado(String id, EstadoLimpieza estado) {
        validarId(id);
        if (estado == null) { throw ApiException.invalida("estado es obligatorio."); }
        return transacciones.execute(status -> {
            var tarea = buscar(id);
            if (tarea.estado() == estado) { return respuesta(tarea); }
            if (tarea.estado().cerrada()) {
                throw ApiException.conflicto("TAREA_CERRADA", "No se puede modificar una tarea completada o cancelada.");
            }
            if (estado == EstadoLimpieza.PENDIENTE) {
                throw ApiException.conflicto("TRANSICION_INVALIDA", "Una tarea en proceso no puede volver a pendiente.");
            }
            return respuesta(repository.actualizarEstado(tarea, estado));
        });
    }

    private Creacion repetir(TareaLimpieza tarea, DatosLimpieza datos) {
        if (!tarea.datos().equals(datos)) {
            throw ApiException.conflicto("IDEMPOTENCY_KEY_REUTILIZADA", "La Idempotency-Key ya se utilizó con otros datos.");
        }
        return new Creacion(LimpiezaResponse.de(tarea, EstadoLimpieza.PENDIENTE), true);
    }
    private TareaLimpieza buscar(String id) {
        validarId(id);
        return repository.buscar(id).orElseThrow(() ->
            new ApiException(HttpStatus.NOT_FOUND, "TAREA_NO_ENCONTRADA", "Tarea de limpieza no encontrada."));
    }
    private LimpiezaResponse respuesta(TareaLimpieza tarea) { return LimpiezaResponse.de(tarea, tarea.estado()); }
    private void validarId(String id) {
        if (id == null || !id.matches("[a-f0-9]{64}")) { throw ApiException.invalida("Identificador inválido."); }
    }
}
