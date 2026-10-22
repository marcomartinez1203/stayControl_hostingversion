package com.example.staycontrol.service;

import com.example.staycontrol.dto.*;
import com.example.staycontrol.exception.ApiException;
import com.example.staycontrol.model.*;
import com.example.staycontrol.repository.ReservaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

@Service
public class ReservaService {
    public record Creacion(ReservaResponse reserva, boolean repetida) { }
    private final ReservaRepository repository;
    private final IdempotencyService idempotencia;
    private final TransactionTemplate transacciones;
    private final ZoneId zona;

    public ReservaService(ReservaRepository repository, IdempotencyService idempotencia,
        MongoTransactionManager manager, @Value("${app.hotel.zone:America/Bogota}") String zona) {
        this.repository = repository;
        this.idempotencia = idempotencia;
        this.transacciones = new TransactionTemplate(manager);
        this.zona = ZoneId.of(zona);
    }

    public Creacion crear(String clave, ReservaRequest request) {
        String id = idempotencia.identificador("POST:/api/v1/reservas", clave);
        if (request == null) { throw ApiException.invalida("El cuerpo JSON es obligatorio."); }
        DatosReserva datos = request.validarYNormalizar();
        var anterior = repository.buscar(id);
        if (anterior.isPresent()) { return repetir(anterior.get(), datos); }
        repository.prepararIndices();
        try {
            return transacciones.execute(status -> {
                var existente = repository.buscar(id);
                if (existente.isPresent()) { return repetir(existente.get(), datos); }
                var habitacion = repository.bloquearHabitacion(datos.habitacionId()).orElseThrow(() ->
                    new ApiException(HttpStatus.NOT_FOUND, "HABITACION_NO_ENCONTRADA", "Habitación no encontrada."));
                if (habitacion.datos().estado() == EstadoHabitacion.MANTENIMIENTO) {
                    throw ApiException.conflicto("HABITACION_NO_DISPONIBLE", "La habitación está en mantenimiento.");
                }
                if (datos.personas() > habitacion.datos().capacidad()) {
                    throw ApiException.conflicto("CAPACIDAD_EXCEDIDA", "Las personas superan la capacidad de la habitación.");
                }
                if (repository.hayCruce(datos)) {
                    throw ApiException.conflicto("FECHAS_OCUPADAS", "Ya existe una reserva confirmada en esas fechas.");
                }
                long noches = ChronoUnit.DAYS.between(request.fechaEntrada(), request.fechaSalida());
                BigDecimal precio = habitacion.datos().precioNoche();
                var nueva = repository.insertar(new Reserva(id, datos, EstadoReserva.CONFIRMADA,
                    precio, precio.multiply(BigDecimal.valueOf(noches)).setScale(2)));
                return new Creacion(ReservaResponse.de(nueva, EstadoReserva.CONFIRMADA), false);
            });
        } catch (DuplicateKeyException exception) {
            return repository.buscar(id).map(reserva -> repetir(reserva, datos))
                .orElseThrow(() -> new DataAccessResourceFailureException("Reintenta con la misma clave."));
        }
    }

    public ReservaResponse obtener(String id) { return respuesta(buscar(id)); }

    /** Consulta orientativa: no escribe contadores, índices ni reservas. */
    public DisponibilidadResponse disponibilidad(String habitacionId, Integer personas, LocalDate entrada, LocalDate salida) {
        ReservaRequest.validarEstancia(habitacionId, personas, entrada, salida);
        var habitacion = repository.consultarHabitacion(habitacionId).orElseThrow(() ->
            new ApiException(HttpStatus.NOT_FOUND, "HABITACION_NO_ENCONTRADA", "Habitación no encontrada."));
        String motivo = null;
        if (habitacion.datos().estado() == EstadoHabitacion.MANTENIMIENTO) {
            motivo = "La habitación está en mantenimiento.";
        } else if (personas > habitacion.datos().capacidad()) {
            motivo = "Las personas superan la capacidad de la habitación.";
        } else if (repository.hayCruce(habitacionId, entrada.toString(), salida.toString())) {
            motivo = "Ya existe una reserva confirmada en esas fechas.";
        }
        long noches = ChronoUnit.DAYS.between(entrada, salida);
        BigDecimal precio = habitacion.datos().precioNoche();
        return new DisponibilidadResponse(habitacionId, habitacion.datos().numero(), entrada, salida,
            personas, noches, motivo == null, motivo, precio, precio.multiply(BigDecimal.valueOf(noches)).setScale(2));
    }

    public PaginaResponse<ReservaResponse> listar(EstadoReserva estado, String habitacionId, int pagina, int tamano) {
        return listar(estado, habitacionId, pagina, tamano, null);
    }

    public PaginaResponse<ReservaResponse> listar(EstadoReserva estado, String habitacionId, int pagina, int tamano, LocalDate fecha) {
        return listar(estado, habitacionId, pagina, tamano, fecha, null);
    }

    public PaginaResponse<ReservaResponse> listar(EstadoReserva estado, String habitacionId, int pagina, int tamano,
        LocalDate fecha, String buscar) {
        String texto = buscar == null ? "" : buscar.strip();
        if (texto.length() > 120 || texto.codePoints().anyMatch(Character::isISOControl)) {
            throw ApiException.invalida("buscar admite hasta 120 caracteres sin controles.");
        }
        if (habitacionId != null) { validarId(habitacionId); }
        if (fecha != null && (fecha.getYear() < 2000 || fecha.getYear() > 2100)) {
            throw ApiException.invalida("fecha debe estar entre 2000 y 2100.");
        }
        if (pagina < 0 || pagina > 10000 || tamano < 1 || tamano > 100) {
            throw ApiException.invalida("pagina debe estar entre 0 y 10000; tamano, entre 1 y 100.");
        }
        String dia = fecha == null ? null : fecha.toString();
        if (!texto.isEmpty()) {
            return new PaginaResponse<>(repository.listar(estado, habitacionId, pagina, tamano, dia, texto).stream()
                .map(this::respuesta).toList(), pagina, tamano, repository.contar(estado, habitacionId, dia, texto));
        }
        return new PaginaResponse<>(repository.listar(estado, habitacionId, pagina, tamano, dia).stream()
            .map(this::respuesta).toList(), pagina, tamano, repository.contar(estado, habitacionId, dia));
    }

    public ReservaResponse cambiarEstado(String id, EstadoReserva estado) {
        validarId(id);
        if (estado != EstadoReserva.CANCELADA && estado != EstadoReserva.FINALIZADA) {
            throw ApiException.invalida("El estado de destino debe ser CANCELADA o FINALIZADA.");
        }
        return transacciones.execute(status -> {
            Reserva reserva = buscar(id);
            if (reserva.estado() == estado) { return respuesta(reserva); }
            if (reserva.estado() != EstadoReserva.CONFIRMADA) {
                throw ApiException.conflicto("RESERVA_CERRADA", "Una reserva cerrada no puede cambiar de estado.");
            }
            if (estado == EstadoReserva.FINALIZADA && LocalDate.parse(reserva.datos().fechaSalida()).isAfter(LocalDate.now(zona))) {
                throw ApiException.conflicto("ESTANCIA_NO_TERMINADA", "No se puede finalizar antes de la fecha de salida.");
            }
            return respuesta(repository.cerrar(reserva, estado));
        });
    }

    private Creacion repetir(Reserva reserva, DatosReserva datos) {
        if (!reserva.datos().equals(datos)) {
            throw ApiException.conflicto("IDEMPOTENCY_KEY_REUTILIZADA", "La Idempotency-Key ya se utilizó con otros datos.");
        }
        // Reproduce la creación original, incluso tras cancelar/finalizar; no cambia el estado actual.
        return new Creacion(ReservaResponse.de(reserva, EstadoReserva.CONFIRMADA), true);
    }

    private Reserva buscar(String id) {
        validarId(id);
        return repository.buscar(id).orElseThrow(() ->
            new ApiException(HttpStatus.NOT_FOUND, "RESERVA_NO_ENCONTRADA", "Reserva no encontrada."));
    }
    private ReservaResponse respuesta(Reserva reserva) { return ReservaResponse.de(reserva, reserva.estado()); }
    private void validarId(String id) {
        if (id == null || !id.matches("[a-f0-9]{64}")) { throw ApiException.invalida("Identificador inválido."); }
    }
}
