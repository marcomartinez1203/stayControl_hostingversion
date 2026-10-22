package com.example.staycontrol.controller.api;

import com.example.staycontrol.dto.HabitacionRequest;
import com.example.staycontrol.dto.HabitacionResponse;
import com.example.staycontrol.dto.PaginaResponse;
import com.example.staycontrol.model.EstadoHabitacion;
import com.example.staycontrol.service.HabitacionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;

@RestController
@RequestMapping("/api/v1/habitaciones")
public class HabitacionController {
    private final HabitacionService service;

    public HabitacionController(HabitacionService service) { this.service = service; }

    @GetMapping
    public PaginaResponse<HabitacionResponse> listar(
        @RequestParam(required = false) EstadoHabitacion estado,
        @RequestParam(defaultValue = "0") int pagina,
        @RequestParam(defaultValue = "20") int tamano,
        @RequestParam(required = false) String buscar
    ) {
        return service.listar(estado, pagina, tamano, buscar);
    }

    @GetMapping("/{id}")
    public HabitacionResponse obtener(@PathVariable String id) { return service.obtener(id); }

    @PostMapping
    public ResponseEntity<HabitacionResponse> crear(
        @RequestHeader(value = "Idempotency-Key", required = false) String clave,
        @RequestBody HabitacionRequest request
    ) {
        var resultado = service.crear(clave, request);
        return ResponseEntity.created(URI.create("/api/v1/habitaciones/" + resultado.habitacion().id()))
            .header("Idempotency-Replayed", Boolean.toString(resultado.repetida()))
            .body(resultado.habitacion());
    }

    @PutMapping("/{id}")
    public HabitacionResponse actualizar(@PathVariable String id, @RequestBody HabitacionRequest request) {
        return service.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable String id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
