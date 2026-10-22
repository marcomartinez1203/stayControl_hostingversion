package com.example.staycontrol.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("tareas_limpieza")
public record TareaLimpieza(@Id String id, DatosLimpieza datos, EstadoLimpieza estado) { }
