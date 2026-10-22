package com.example.staycontrol.dto;

import com.example.staycontrol.model.EstadoReserva;
import com.example.staycontrol.model.Reserva;
import java.math.BigDecimal;

public record ReservaResponse(String id, String habitacionId, String huesped, int personas,
    String fechaEntrada, String fechaSalida, EstadoReserva estado, BigDecimal precioNoche, BigDecimal total) {
    public static ReservaResponse de(Reserva reserva, EstadoReserva estado) {
        var datos = reserva.datos();
        return new ReservaResponse(reserva.id(), datos.habitacionId(), datos.huesped(), datos.personas(),
            datos.fechaEntrada(), datos.fechaSalida(), estado, reserva.precioNoche(), reserva.total());
    }
}
