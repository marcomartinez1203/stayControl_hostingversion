package com.example.staycontrol.service;

import com.example.staycontrol.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class IdempotencyServiceTests {
    private final IdempotencyService service = new IdempotencyService();

    @Test
    void identidadEstableSeparadaPorOperacion() {
        String id = service.identificador("POST:/habitaciones", "clave-1");
        assertTrue(id.matches("[a-f0-9]{64}"));
        assertEquals(id, new IdempotencyService().identificador("POST:/habitaciones", "clave-1"));
        assertNotEquals(id, service.identificador("POST:/reservas", "clave-1"));
        assertNotEquals(id, service.identificador("POST:/habitaciones", "clave-2"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "una clave", "clave/1", "clave\n1", "áéí", "clave,otra"})
    void rechazaClavesInvalidas(String clave) {
        assertThrows(ApiException.class, () -> service.identificador("operacion", clave));
    }

    @Test
    void limitaLaLongitud() {
        assertDoesNotThrow(() -> service.identificador("operacion", "a".repeat(128)));
        assertThrows(ApiException.class, () -> service.identificador("operacion", "a".repeat(129)));
    }
}
