package com.example.staycontrol.dto;

import com.example.staycontrol.exception.ApiException;
import com.example.staycontrol.model.DatosLimpieza;
import java.time.LocalDate;

public record LimpiezaRequest(String habitacionId, String descripcion, String responsable, LocalDate fechaProgramada) {
    public DatosLimpieza validarYNormalizar() {
        if (habitacionId == null || !habitacionId.matches("[a-f0-9]{64}")) {
            throw ApiException.invalida("habitacionId debe ser el identificador devuelto por habitaciones.");
        }
        String detalle = descripcion == null ? "" : descripcion.strip();
        String asignada = responsable == null ? "" : responsable.strip();
        if (detalle.isEmpty() || detalle.length() > 500 || detalle.codePoints().anyMatch(Character::isISOControl)) {
            throw ApiException.invalida("descripcion debe tener entre 1 y 500 caracteres sin controles.");
        }
        if (asignada.length() > 120 || asignada.codePoints().anyMatch(Character::isISOControl)) {
            throw ApiException.invalida("responsable admite hasta 120 caracteres sin controles.");
        }
        if (fechaProgramada == null || fechaProgramada.getYear() < 2000 || fechaProgramada.getYear() > 2100) {
            throw ApiException.invalida("fechaProgramada es obligatoria y debe estar entre 2000 y 2100.");
        }
        return new DatosLimpieza(habitacionId, detalle, asignada.isEmpty() ? null : asignada, fechaProgramada.toString());
    }
}
