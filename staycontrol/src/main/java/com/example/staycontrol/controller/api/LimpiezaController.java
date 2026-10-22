package com.example.staycontrol.controller.api;

import com.example.staycontrol.dto.*;
import com.example.staycontrol.model.EstadoLimpieza;
import com.example.staycontrol.service.LimpiezaService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/limpieza")
public class LimpiezaController {
    public record EstadoRequest(EstadoLimpieza estado) { }
    private final LimpiezaService service;
    public LimpiezaController(LimpiezaService service) { this.service = service; }

    @GetMapping
    public PaginaResponse<LimpiezaResponse> listar(@RequestParam(required = false) EstadoLimpieza estado,
        @RequestParam(required = false) String habitacionId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
        @RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "20") int tamano) {
        return service.listar(estado, habitacionId, fecha, pagina, tamano);
    }
    @GetMapping("/{id}")
    public LimpiezaResponse obtener(@PathVariable String id) { return service.obtener(id); }

    @PostMapping
    public ResponseEntity<LimpiezaResponse> crear(@RequestHeader(value = "Idempotency-Key", required = false) String clave,
        @RequestBody LimpiezaRequest request) {
        var resultado = service.crear(clave, request);
        return ResponseEntity.created(URI.create("/api/v1/limpieza/" + resultado.tarea().id()))
            .header("Idempotency-Replayed", Boolean.toString(resultado.repetida())).body(resultado.tarea());
    }
    @PutMapping("/{id}/estado")
    public LimpiezaResponse cambiarEstado(@PathVariable String id, @RequestBody EstadoRequest request) {
        return service.cambiarEstado(id, request.estado());
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelar(@PathVariable String id) {
        service.cambiarEstado(id, EstadoLimpieza.CANCELADA);
        return ResponseEntity.noContent().build();
    }
}
