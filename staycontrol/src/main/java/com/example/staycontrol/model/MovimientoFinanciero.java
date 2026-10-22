package com.example.staycontrol.model;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("movimientos_financieros")
public record MovimientoFinanciero(@Id String id, DatosMovimiento datos, EstadoMovimiento estado,
    String creadoPor, Instant creadoEn, Anulacion anulacion) {
    public record Anulacion(String motivo, String realizadoPor, Instant fecha) { }
}
