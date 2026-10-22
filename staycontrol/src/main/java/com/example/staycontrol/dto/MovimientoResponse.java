package com.example.staycontrol.dto;

import com.example.staycontrol.model.*;
import java.math.BigDecimal;
import java.time.Instant;

public record MovimientoResponse(String id, TipoMovimiento tipo, String concepto, BigDecimal importe,
    String moneda, String fecha, MetodoPago metodoPago, EstadoMovimiento estado,
    String creadoPor, Instant creadoEn, MovimientoFinanciero.Anulacion anulacion) {
    public static MovimientoResponse de(MovimientoFinanciero movimiento, boolean original) {
        var datos = movimiento.datos();
        return new MovimientoResponse(movimiento.id(), datos.tipo(), datos.concepto(), datos.importe(),
            datos.moneda(), datos.fecha(), datos.metodoPago(), original ? EstadoMovimiento.REGISTRADO : movimiento.estado(),
            movimiento.creadoPor(), movimiento.creadoEn(), original ? null : movimiento.anulacion());
    }
}
