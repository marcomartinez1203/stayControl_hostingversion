package com.example.staycontrol.service;

import com.example.staycontrol.exception.ApiException;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoginAttemptLimiterTests {
    @Test void limitaPorIpYPermiteReintentarAlTerminarLaVentana() {
        Clock clock = mock(Clock.class);
        when(clock.millis()).thenReturn(0L);
        var limiter = new LoginAttemptLimiter(clock);
        for (int i = 0; i < 10; i++) { limiter.comprobar("127.0.0.1"); }
        assertEquals(429, assertThrows(ApiException.class, () -> limiter.comprobar("127.0.0.1")).status().value());
        assertDoesNotThrow(() -> limiter.comprobar("127.0.0.2"));
        when(clock.millis()).thenReturn(60_000L);
        assertDoesNotThrow(() -> limiter.comprobar("127.0.0.1"));
    }
}
