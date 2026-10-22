package com.example.staycontrol.service;

import com.example.staycontrol.dto.HabitacionRequest;
import com.example.staycontrol.dto.HabitacionResponse;
import com.example.staycontrol.dto.PaginaResponse;
import com.example.staycontrol.exception.ApiException;
import com.example.staycontrol.model.DatosHabitacion;
import com.example.staycontrol.model.EstadoHabitacion;
import com.example.staycontrol.model.Habitacion;
import com.example.staycontrol.repository.HabitacionRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class HabitacionService {
    private static final String OPERACION = "POST:/api/v1/habitaciones";
    private final HabitacionRepository repository;
    private final IdempotencyService idempotencia;

    public HabitacionService(HabitacionRepository repository, IdempotencyService idempotencia) {
        this.repository = repository;
        this.idempotencia = idempotencia;
    }

    public record Creacion(HabitacionResponse habitacion, boolean repetida) { }

    public Creacion crear(String clave, HabitacionRequest request) {
        String id = idempotencia.identificador(OPERACION, clave);
        DatosHabitacion datos = validar(request);
        var existente = repository.buscarPorId(id);
        if (existente.isPresent()) { return repetir(existente.get(), datos); }

        try {
            Habitacion nueva = repository.insertar(new Habitacion(id, datos, datos, false));
            return new Creacion(HabitacionResponse.de(nueva.id(), nueva.creacion()), false);
        } catch (DuplicateKeyException exception) {
            // Otra petición puede haber insertado la misma clave entre nuestra lectura y el insert.
            var ganadora = repository.buscarPorId(id);
            if (ganadora.isPresent()) { return repetir(ganadora.get(), datos); }
            if (repository.buscarPorNumero(datos.numero()).filter(h -> !h.id().equals(id)).isPresent()) {
                throw ApiException.conflicto("NUMERO_DUPLICADO",
                    "Ya existe una habitación con ese número, incluso si fue eliminada.");
            }
            // Una escritura concurrente puede no ser visible todavía con readConcern majority.
            throw new DataAccessResourceFailureException("Resultado concurrente pendiente de confirmación.");
        }
    }

    public PaginaResponse<HabitacionResponse> listar(EstadoHabitacion estado, int pagina, int tamano) {
        if (pagina < 0 || pagina > 10000 || tamano < 1 || tamano > 100) {
            throw ApiException.invalida("pagina debe estar entre 0 y 10000; tamano, entre 1 y 100.");
        }
        var items = repository.listar(estado, pagina, tamano).stream()
            .map(habitacion -> HabitacionResponse.de(habitacion.id(), habitacion.datos())).toList();
        return new PaginaResponse<>(items, pagina, tamano, repository.contar(estado));
    }

    public PaginaResponse<HabitacionResponse> listar(EstadoHabitacion estado, int pagina, int tamano, String buscar) {
        String texto = buscar == null ? "" : buscar.strip();
        if (texto.length() > 60 || texto.codePoints().anyMatch(Character::isISOControl)) {
            throw ApiException.invalida("buscar admite hasta 60 caracteres sin controles.");
        }
        if (texto.isEmpty()) { return listar(estado, pagina, tamano); }
        if (pagina < 0 || pagina > 10000 || tamano < 1 || tamano > 100) {
            throw ApiException.invalida("pagina debe estar entre 0 y 10000; tamano, entre 1 y 100.");
        }
        var items = repository.listar(estado, pagina, tamano, texto).stream()
            .map(habitacion -> HabitacionResponse.de(habitacion.id(), habitacion.datos())).toList();
        return new PaginaResponse<>(items, pagina, tamano, repository.contar(estado, texto));
    }

    public HabitacionResponse obtener(String id) {
        Habitacion habitacion = buscarActiva(id);
        return HabitacionResponse.de(habitacion.id(), habitacion.datos());
    }

    public HabitacionResponse actualizar(String id, HabitacionRequest request) {
        DatosHabitacion datos = validar(request);
        Habitacion actual = buscarActiva(id);
        if (!actual.datos().numero().equals(datos.numero())) {
            throw ApiException.conflicto("NUMERO_INMUTABLE", "No se puede cambiar el número de una habitación.");
        }
        Habitacion actualizada = repository.actualizar(id, datos).orElseThrow(this::noEncontrada);
        return HabitacionResponse.de(actualizada.id(), actualizada.datos());
    }

    public void eliminar(String id) {
        validarId(id);
        repository.eliminar(id);
    }

    private Creacion repetir(Habitacion habitacion, DatosHabitacion datos) {
        if (!habitacion.creacion().coincideCon(datos)) {
            throw ApiException.conflicto("IDEMPOTENCY_KEY_REUTILIZADA",
                "La Idempotency-Key ya se utilizó con otros datos.");
        }
        return new Creacion(HabitacionResponse.de(habitacion.id(), habitacion.creacion()), true);
    }

    private Habitacion buscarActiva(String id) {
        validarId(id);
        return repository.buscarPorId(id).filter(habitacion -> !habitacion.eliminada())
            .orElseThrow(this::noEncontrada);
    }

    private DatosHabitacion validar(HabitacionRequest request) {
        if (request == null) { throw ApiException.invalida("El cuerpo JSON es obligatorio."); }
        return request.validarYNormalizar();
    }

    private void validarId(String id) {
        if (id == null || !id.matches("[a-f0-9]{64}")) {
            throw ApiException.invalida("id debe ser el identificador devuelto al crear la habitación.");
        }
    }

    private ApiException noEncontrada() {
        return new ApiException(HttpStatus.NOT_FOUND, "HABITACION_NO_ENCONTRADA", "Habitación no encontrada.");
    }
}
