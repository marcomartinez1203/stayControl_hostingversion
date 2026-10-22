package com.example.staycontrol.controller.api;

import com.example.staycontrol.dto.DashboardResponse;
import com.example.staycontrol.service.DashboardService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {
    private final DashboardService service;
    public DashboardController(DashboardService service) { this.service = service; }

    @GetMapping
    public DashboardResponse consultar(@RequestParam String moneda,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
        @RequestParam(defaultValue = "5") int limiteReservas) {
        return service.consultar(moneda, fecha, limiteReservas);
    }
}
