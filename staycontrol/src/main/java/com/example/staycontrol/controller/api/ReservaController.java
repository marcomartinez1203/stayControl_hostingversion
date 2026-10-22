package com.example.staycontrol.controller.api;

import com.example.staycontrol.dto.*;
import com.example.staycontrol.model.EstadoReserva;
import com.example.staycontrol.service.ReservaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

@RestController
@RequestMapping("/api/v1/reservas")
public class ReservaController {
    public record EstadoRequest(EstadoReserva estado) { }
    private final ReservaService service;
    public ReservaController(ReservaService service) { this.service = service; }

    @GetMapping
    public PaginaResponse<ReservaResponse> listar(@RequestParam(required = false) EstadoReserva estado,
        @RequestParam(required = false) String habitacionId, @RequestParam(defaultValue = "0") int pagina,
        @RequestParam(defaultValue = "20") int tamano,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
        @RequestParam(required = false) String buscar) {
        return service.listar(estado, habitacionId, pagina, tamano, fecha, buscar);
    }
    @GetMapping("/{id}")
    public ReservaResponse obtener(@PathVariable String id) { return service.obtener(id); }

    @GetMapping("/disponibilidad")
    public ResponseEntity<DisponibilidadResponse> disponibilidad(@RequestParam String habitacionId,
        @RequestParam Integer personas,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaEntrada,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaSalida) {
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
            .body(service.disponibilidad(habitacionId, personas, fechaEntrada, fechaSalida));
    }

    @PostMapping
    public ResponseEntity<ReservaResponse> crear(@RequestHeader(value = "Idempotency-Key", required = false) String clave,
        @RequestBody ReservaRequest request) {
        var resultado = service.crear(clave, request);
        return ResponseEntity.created(URI.create("/api/v1/reservas/" + resultado.reserva().id()))
            .header("Idempotency-Replayed", Boolean.toString(resultado.repetida())).body(resultado.reserva());
    }
    @PutMapping("/{id}/estado")
    public ReservaResponse cambiarEstado(@PathVariable String id, @RequestBody EstadoRequest request) {
        return service.cambiarEstado(id, request.estado());
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelar(@PathVariable String id) {
        service.cambiarEstado(id, EstadoReserva.CANCELADA);
        return ResponseEntity.noContent().build();
    }
}
