package com.example.staycontrol.model;

import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;

public record DatosHabitacion(
    String numero,
    String tipo,
    int capacidad,
    @Field(targetType = FieldType.DECIMAL128) BigDecimal precioNoche,
    EstadoHabitacion estado
) {
    public boolean coincideCon(DatosHabitacion otro) {
        return numero.equals(otro.numero) && tipo.equals(otro.tipo)
            && capacidad == otro.capacidad && precioNoche.compareTo(otro.precioNoche) == 0
            && estado == otro.estado;
    }
}
