package com.example.staycontrol.dto;

import com.example.staycontrol.model.DatosHabitacion;
import com.example.staycontrol.model.EstadoHabitacion;
import java.math.BigDecimal;

/** Nunca serializar directamente el documento: contiene metadatos internos de idempotencia. */
public record HabitacionResponse(
    String id, String numero, String tipo, int capacidad, BigDecimal precioNoche, EstadoHabitacion estado
) {
    public static HabitacionResponse de(String id, DatosHabitacion datos) {
        return new HabitacionResponse(id, datos.numero(), datos.tipo(), datos.capacidad(),
            datos.precioNoche(), datos.estado());
    }
}
