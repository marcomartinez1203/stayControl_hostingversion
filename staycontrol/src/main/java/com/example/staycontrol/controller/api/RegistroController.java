package com.example.staycontrol.controller.api;

import com.example.staycontrol.dto.RegistroRequest;
import com.example.staycontrol.service.LoginAttemptLimiter;
import com.example.staycontrol.service.RegistroService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class RegistroController {
    public record RegistroResponse(String email, String usuario, String rol, String nombreHotel) { }
    private final RegistroService service;
    private final LoginAttemptLimiter limiter;

    public RegistroController(RegistroService service, LoginAttemptLimiter limiter) {
        this.service = service;
        this.limiter = limiter;
    }

    @PostMapping("/registro")
    public ResponseEntity<RegistroResponse> registrar(@RequestBody RegistroRequest datos,
        @RequestHeader(value = "Idempotency-Key", required = false) String clave,
        HttpServletRequest request) {
        limiter.comprobar(request.getRemoteAddr());
        var resultado = service.registrar(datos, clave);
        return ResponseEntity.status(201)
            .header("Idempotency-Replayed", Boolean.toString(resultado.repetido()))
            .header("Cache-Control", "no-store")
            .body(new RegistroResponse(resultado.email(), resultado.usuario(), resultado.rol(), resultado.nombreHotel()));
    }
}
