package com.example.staycontrol.controller.api;

import com.example.staycontrol.dto.*;
import com.example.staycontrol.model.*;
import com.example.staycontrol.service.FinanzasService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/finanzas")
public class FinanzasController {
    public record AnulacionRequest(String motivo) { }
    private final FinanzasService service;
    public FinanzasController(FinanzasService service) { this.service = service; }

    @GetMapping("/movimientos")
    public PaginaResponse<MovimientoResponse> listar(@RequestParam(required = false) TipoMovimiento tipo,
        @RequestParam(required = false) EstadoMovimiento estado, @RequestParam(required = false) String moneda,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
        @RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "20") int tamano) {
        return service.listar(tipo, estado, moneda, desde, hasta, pagina, tamano);
    }
    @GetMapping("/movimientos/{id}")
    public MovimientoResponse obtener(@PathVariable String id) { return service.obtener(id); }

    @PostMapping("/movimientos")
    public ResponseEntity<MovimientoResponse> crear(@RequestHeader(value = "Idempotency-Key", required = false) String clave,
        @RequestBody MovimientoRequest request, Authentication authentication) {
        var resultado = service.crear(clave, request, authentication.getName());
        return ResponseEntity.created(URI.create("/api/v1/finanzas/movimientos/" + resultado.movimiento().id()))
            .header("Idempotency-Replayed", Boolean.toString(resultado.repetida())).body(resultado.movimiento());
    }
    @PutMapping("/movimientos/{id}/anulacion")
    public MovimientoResponse anular(@PathVariable String id, @RequestBody AnulacionRequest request,
        Authentication authentication) {
        return service.anular(id, request.motivo(), authentication.getName());
    }
    @GetMapping("/resumen")
    public ResumenFinanzasResponse resumir(@RequestParam String moneda,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return service.resumir(moneda, desde, hasta);
    }
}
