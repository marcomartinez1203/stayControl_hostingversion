package com.example.staycontrol.model;

import java.math.BigDecimal;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

@Document("reservas")
public record Reserva(@Id String id, DatosReserva datos, EstadoReserva estado,
    @Field(targetType = FieldType.DECIMAL128) BigDecimal precioNoche,
    @Field(targetType = FieldType.DECIMAL128) BigDecimal total) { }
