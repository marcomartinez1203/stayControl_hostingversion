package com.example.staycontrol.dto;

import java.math.BigDecimal;
import java.util.List;

public record DashboardResponse(String fecha, String zonaHoraria, Habitaciones habitacionesActuales,
    ReservasDia reservasDelDia, Limpieza limpieza, ResumenFinanzasResponse finanzasMes,
    List<ProximaReserva> proximasReservas) {
    public DashboardResponse { proximasReservas = List.copyOf(proximasReservas); }

    public record Habitaciones(long total, long disponibles, long ocupadas, long enLimpieza,
        long enMantenimiento, BigDecimal porcentajeOcupacion) { }
    public record ReservasDia(long entradasPrevistas, long salidasPrevistas, long estanciasProgramadas) { }
    public record Limpieza(long pendientes, long enProceso, long habitacionesConTareas) { }
    public record ProximaReserva(String id, String habitacionId, String numeroHabitacion,
        String huesped, int personas, String fechaEntrada, String fechaSalida) { }
}
