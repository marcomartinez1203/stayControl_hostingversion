package com.example.staycontrol.dto;

import com.example.staycontrol.model.EstadoLimpieza;
import com.example.staycontrol.model.TareaLimpieza;

public record LimpiezaResponse(String id, String habitacionId, String descripcion, String responsable,
    String fechaProgramada, EstadoLimpieza estado) {
    public static LimpiezaResponse de(TareaLimpieza tarea, EstadoLimpieza estado) {
        var datos = tarea.datos();
        return new LimpiezaResponse(tarea.id(), datos.habitacionId(), datos.descripcion(), datos.responsable(),
            datos.fechaProgramada(), estado);
    }
}
