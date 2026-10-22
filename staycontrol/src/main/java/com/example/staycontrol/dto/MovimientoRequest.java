package com.example.staycontrol.dto;

import com.example.staycontrol.exception.ApiException;
import com.example.staycontrol.model.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Locale;

public record MovimientoRequest(TipoMovimiento tipo, String concepto, BigDecimal importe,
    String moneda, LocalDate fecha, MetodoPago metodoPago) {
    public DatosMovimiento validarYNormalizar() {
        String descripcion = concepto == null ? "" : concepto.strip();
        if (tipo == null || descripcion.isEmpty() || descripcion.length() > 200
            || descripcion.codePoints().anyMatch(Character::isISOControl)) {
            throw ApiException.invalida("tipo es obligatorio; concepto debe tener de 1 a 200 caracteres sin controles.");
        }
        String divisa = normalizarMoneda(moneda);
        if (importe == null || importe.signum() <= 0 || importe.compareTo(new BigDecimal("999999999999.9999")) > 0) {
            throw ApiException.invalida("importe debe ser positivo y no superar 999999999999.9999.");
        }
        BigDecimal normalizado;
        try {
            normalizado = importe.setScale(Currency.getInstance(divisa).getDefaultFractionDigits(), RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw ApiException.invalida("El importe tiene más decimales de los permitidos por la moneda.");
        }
        if (fecha == null || fecha.getYear() < 2000 || fecha.getYear() > 2100) {
            throw ApiException.invalida("fecha debe estar entre 2000 y 2100.");
        }
        return new DatosMovimiento(tipo, descripcion, normalizado, divisa, fecha.toString(),
            metodoPago == null ? MetodoPago.OTRO : metodoPago);
    }

    public static String normalizarMoneda(String moneda) {
        String codigo = moneda == null ? "" : moneda.strip().toUpperCase(Locale.ROOT);
        try {
            if (!codigo.matches("[A-Z]{3}")) { throw new IllegalArgumentException(); }
            int decimales = Currency.getInstance(codigo).getDefaultFractionDigits();
            if (decimales < 0 || decimales > 4) { throw new IllegalArgumentException(); }
            return codigo;
        } catch (IllegalArgumentException exception) {
            throw ApiException.invalida("moneda debe ser un código ISO 4217 admitido, por ejemplo COP, USD o EUR.");
        }
    }
}
