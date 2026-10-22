package com.example.staycontrol.dto;

import com.example.staycontrol.exception.ApiException;
import com.example.staycontrol.model.EstadoHabitacion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.math.BigDecimal;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

class HabitacionRequestTests {
    @Test
    void normalizaElContenidoAntesDeCompararIdempotencia() {
        var datos = new HabitacionRequest(" a-101 ", " Individual ", 1,
            new BigDecimal("25.000"), EstadoHabitacion.DISPONIBLE).validarYNormalizar();
        assertEquals("A-101", datos.numero());
        assertEquals("Individual", datos.tipo());
        assertEquals(new BigDecimal("25.00"), datos.precioNoche());
    }

    static Stream<HabitacionRequest> invalidas() {
        var estado = EstadoHabitacion.DISPONIBLE;
        var precio = BigDecimal.TEN;
        return Stream.of(
            new HabitacionRequest(null, "Individual", 1, precio, estado),
            new HabitacionRequest(" ", "Individual", 1, precio, estado),
            new HabitacionRequest("../101", "Individual", 1, precio, estado),
            new HabitacionRequest("a".repeat(21), "Individual", 1, precio, estado),
            new HabitacionRequest("101", null, 1, precio, estado),
            new HabitacionRequest("101", " ", 1, precio, estado),
            new HabitacionRequest("101", "a".repeat(61), 1, precio, estado),
            new HabitacionRequest("101", "Una\nOtra", 1, precio, estado),
            new HabitacionRequest("101", "Individual", null, precio, estado),
            new HabitacionRequest("101", "Individual", 0, precio, estado),
            new HabitacionRequest("101", "Individual", 21, precio, estado),
            new HabitacionRequest("101", "Individual", 1, null, estado),
            new HabitacionRequest("101", "Individual", 1, BigDecimal.ZERO, estado),
            new HabitacionRequest("101", "Individual", 1, new BigDecimal("-1"), estado),
            new HabitacionRequest("101", "Individual", 1, new BigDecimal("1.001"), estado),
            new HabitacionRequest("101", "Individual", 1, new BigDecimal("1000000000"), estado),
            new HabitacionRequest("101", "Individual", 1, precio, null)
        );
    }

    @ParameterizedTest
    @MethodSource("invalidas")
    void rechazaDatosInvalidos(HabitacionRequest request) {
        assertEquals(400, assertThrows(ApiException.class, request::validarYNormalizar).status().value());
    }
}
