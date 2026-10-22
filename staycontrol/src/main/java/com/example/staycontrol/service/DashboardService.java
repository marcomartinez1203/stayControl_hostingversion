package com.example.staycontrol.service;

import com.example.staycontrol.dto.DashboardResponse;
import com.example.staycontrol.dto.MovimientoRequest;
import com.example.staycontrol.exception.ApiException;
import com.example.staycontrol.repository.DashboardRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.ZoneId;

@Service
public class DashboardService {
    private final DashboardRepository repository;
    private final FinanzasService finanzas;
    private final ZoneId zona;

    public DashboardService(DashboardRepository repository, FinanzasService finanzas,
        @Value("${app.hotel.zone:America/Bogota}") String zona) {
        this.repository = repository;
        this.finanzas = finanzas;
        this.zona = ZoneId.of(zona);
    }

    public DashboardResponse consultar(String moneda, LocalDate fecha, int limiteReservas) {
        String divisa = MovimientoRequest.normalizarMoneda(moneda);
        LocalDate referencia = fecha == null ? LocalDate.now(zona) : fecha;
        if (referencia.getYear() < 2000 || referencia.getYear() > 2100 || limiteReservas < 1 || limiteReservas > 20) {
            throw ApiException.invalida("fecha debe estar entre 2000 y 2100; limiteReservas, entre 1 y 20.");
        }
        String dia = referencia.toString();
        return new DashboardResponse(dia, zona.getId(), repository.habitaciones(),
            repository.reservasDelDia(dia), repository.limpieza(dia),
            finanzas.resumir(divisa, referencia.withDayOfMonth(1), referencia.withDayOfMonth(referencia.lengthOfMonth())),
            repository.proximasReservas(dia, limiteReservas));
    }
}
