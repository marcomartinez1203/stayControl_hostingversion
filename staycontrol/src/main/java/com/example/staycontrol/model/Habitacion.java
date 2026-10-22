package com.example.staycontrol.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** La creación original se conserva en el mismo documento, incluso tras la baja lógica. */
@Document(collection = "habitaciones")
public record Habitacion(
    @Id String id,
    DatosHabitacion datos,
    DatosHabitacion creacion,
    boolean eliminada
) {
}
