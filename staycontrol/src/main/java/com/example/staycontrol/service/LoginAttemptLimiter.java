package com.example.staycontrol.service;

import com.example.staycontrol.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

/** Límite local por IP, acotado en memoria. No confía en X-Forwarded-For del cliente. */
@Service
public class LoginAttemptLimiter {
    private record Ventana(long inicio, int intentos) { }
    private final Map<String, Ventana> ventanas = new HashMap<>();
    private final Clock clock;

    public LoginAttemptLimiter() { this(Clock.systemUTC()); }
    public LoginAttemptLimiter(Clock clock) { this.clock = clock; }

    public synchronized void comprobar(String ip) {
        long ahora = clock.millis();
        ventanas.entrySet().removeIf(entry -> ahora - entry.getValue().inicio() >= 60_000);
        Ventana ventana = ventanas.get(ip);
        if ((ventana == null && ventanas.size() >= 10_000) || (ventana != null && ventana.intentos() >= 10)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "DEMASIADOS_INTENTOS",
                "Demasiados intentos. Espera un minuto antes de volver a intentarlo.");
        }
        ventanas.put(ip, ventana == null ? new Ventana(ahora, 1) : new Ventana(ventana.inicio(), ventana.intentos() + 1));
    }
}
