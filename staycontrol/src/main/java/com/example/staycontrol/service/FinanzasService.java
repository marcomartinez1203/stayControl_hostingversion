package com.example.staycontrol.service;

import com.example.staycontrol.dto.*;
import com.example.staycontrol.exception.ApiException;
import com.example.staycontrol.model.*;
import com.example.staycontrol.repository.FinanzasRepository;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Service
public class FinanzasService {
    public record Creacion(MovimientoResponse movimiento, boolean repetida) { }
    private final FinanzasRepository repository;
    private final IdempotencyService idempotencia;
    public FinanzasService(FinanzasRepository repository, IdempotencyService idempotencia) {
        this.repository = repository;
        this.idempotencia = idempotencia;
    }

    public Creacion crear(String clave, MovimientoRequest request, String operador) {
        String id = idempotencia.identificador("POST:/api/v1/finanzas/movimientos", clave);
        if (request == null) { throw ApiException.invalida("El cuerpo JSON es obligatorio."); }
        DatosMovimiento datos = request.validarYNormalizar();
        var anterior = repository.buscar(id);
        if (anterior.isPresent()) { return repetir(anterior.get(), datos); }
        repository.prepararIndices();
        try {
            var nuevo = repository.insertar(new MovimientoFinanciero(id, datos, EstadoMovimiento.REGISTRADO,
                operador, ahora(), null));
            return new Creacion(MovimientoResponse.de(nuevo, true), false);
        } catch (DuplicateKeyException exception) {
            return repository.buscar(id).map(movimiento -> repetir(movimiento, datos))
                .orElseThrow(() -> new DataAccessResourceFailureException("Reintenta con la misma clave."));
        }
    }

    public MovimientoResponse obtener(String id) { return MovimientoResponse.de(buscar(id), false); }

    public MovimientoResponse anular(String id, String motivo, String operador) {
        validarId(id);
        String razon = motivo == null ? "" : motivo.strip();
        if (razon.isEmpty() || razon.length() > 300 || razon.codePoints().anyMatch(Character::isISOControl)) {
            throw ApiException.invalida("motivo debe tener entre 1 y 300 caracteres sin controles.");
        }
        var actualizado = repository.anular(id, new MovimientoFinanciero.Anulacion(razon, operador, ahora()));
        if (actualizado.isPresent()) { return MovimientoResponse.de(actualizado.get(), false); }
        var actual = buscar(id);
        if (actual.estado() == EstadoMovimiento.REGISTRADO) {
            throw new DataAccessResourceFailureException("Anulación concurrente pendiente de confirmación.");
        }
        if (actual.estado() == EstadoMovimiento.ANULADO && actual.anulacion() != null
            && razon.equals(actual.anulacion().motivo())) {
            return MovimientoResponse.de(actual, false);
        }
        throw ApiException.conflicto("MOVIMIENTO_ANULADO", "El movimiento ya fue anulado con otro motivo.");
    }

    public PaginaResponse<MovimientoResponse> listar(TipoMovimiento tipo, EstadoMovimiento estado, String moneda,
        LocalDate desde, LocalDate hasta, int pagina, int tamano) {
        validarPeriodo(desde, hasta);
        if (pagina < 0 || pagina > 10000 || tamano < 1 || tamano > 100) {
            throw ApiException.invalida("pagina debe estar entre 0 y 10000; tamano, entre 1 y 100.");
        }
        String divisa = moneda == null ? null : MovimientoRequest.normalizarMoneda(moneda);
        return new PaginaResponse<>(repository.listar(tipo, estado, divisa, texto(desde), texto(hasta), pagina, tamano)
            .stream().map(movimiento -> MovimientoResponse.de(movimiento, false)).toList(), pagina, tamano,
            repository.contar(tipo, estado, divisa, texto(desde), texto(hasta)));
    }

    public ResumenFinanzasResponse resumir(String moneda, LocalDate desde, LocalDate hasta) {
        validarPeriodo(desde, hasta);
        return repository.resumir(MovimientoRequest.normalizarMoneda(moneda), texto(desde), texto(hasta));
    }

    private Creacion repetir(MovimientoFinanciero movimiento, DatosMovimiento datos) {
        if (!movimiento.datos().coincideCon(datos)) {
            throw ApiException.conflicto("IDEMPOTENCY_KEY_REUTILIZADA", "La Idempotency-Key ya se utilizó con otros datos.");
        }
        return new Creacion(MovimientoResponse.de(movimiento, true), true);
    }
    private MovimientoFinanciero buscar(String id) {
        validarId(id);
        return repository.buscar(id).orElseThrow(() ->
            new ApiException(HttpStatus.NOT_FOUND, "MOVIMIENTO_NO_ENCONTRADO", "Movimiento no encontrado."));
    }
    private void validarId(String id) {
        if (id == null || !id.matches("[a-f0-9]{64}")) { throw ApiException.invalida("Identificador inválido."); }
    }
    private void validarPeriodo(LocalDate desde, LocalDate hasta) {
        if ((desde != null && (desde.getYear() < 2000 || desde.getYear() > 2100))
            || (hasta != null && (hasta.getYear() < 2000 || hasta.getYear() > 2100))
            || (desde != null && hasta != null && desde.isAfter(hasta))) {
            throw ApiException.invalida("Periodo inválido: fechas entre 2000 y 2100, desde no posterior a hasta.");
        }
    }
    private String texto(LocalDate fecha) { return fecha == null ? null : fecha.toString(); }
    private Instant ahora() { return Instant.now().truncatedTo(ChronoUnit.MILLIS); }
}
