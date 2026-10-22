package com.example.staycontrol.service;

import com.example.staycontrol.dto.HabitacionRequest;
import com.example.staycontrol.exception.ApiException;
import com.example.staycontrol.model.EstadoHabitacion;
import com.example.staycontrol.model.Habitacion;
import com.example.staycontrol.repository.HabitacionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HabitacionServiceTests {
    private final HabitacionRepository repository = mock(HabitacionRepository.class);
    private final IdempotencyService idempotencia = new IdempotencyService();
    private final HabitacionService service = new HabitacionService(repository, idempotencia);
    private final HabitacionRequest request = request("101", "25.00", EstadoHabitacion.DISPONIBLE);
    private final String id = idempotencia.identificador("POST:/api/v1/habitaciones", "prueba-1");

    private static HabitacionRequest request(String numero, String precio, EstadoHabitacion estado) {
        return new HabitacionRequest(numero, "Individual", 1, new BigDecimal(precio), estado);
    }

    private Habitacion original() {
        var datos = request.validarYNormalizar();
        return new Habitacion(id, datos, datos, false);
    }

    @Test
    void creaConLaRespuestaOriginalEnElMismoInsert() {
        when(repository.insertar(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var resultado = service.crear("prueba-1", request);
        assertFalse(resultado.repetida());
        assertEquals(id, resultado.habitacion().id());
        verify(repository).insertar(original());
    }

    @Test
    void repeticionTrasReiniciarElServicioNoInsertaDeNuevo() {
        when(repository.buscarPorId(id)).thenReturn(Optional.of(original()));
        var reiniciado = new HabitacionService(repository, new IdempotencyService());
        var resultado = reiniciado.crear("prueba-1", request(" 101 ", "25.000", EstadoHabitacion.DISPONIBLE));
        assertTrue(resultado.repetida());
        assertEquals(new BigDecimal("25.00"), resultado.habitacion().precioNoche());
        verify(repository, never()).insertar(any());
    }

    @Test
    void mismaClaveConOtrosDatosEsConflicto() {
        when(repository.buscarPorId(id)).thenReturn(Optional.of(original()));
        var error = assertThrows(ApiException.class,
            () -> service.crear("prueba-1", request("102", "25.00", EstadoHabitacion.DISPONIBLE)));
        assertEquals("IDEMPOTENCY_KEY_REUTILIZADA", error.codigo());
        assertEquals(409, error.status().value());
        verify(repository, never()).insertar(any());
    }

    @Test
    void duplicadoConcurrenteReproduceLaCreacionGanadora() {
        when(repository.buscarPorId(id)).thenReturn(Optional.empty(), Optional.of(original()));
        when(repository.insertar(any())).thenThrow(new DuplicateKeyException("duplicada"));
        assertTrue(service.crear("prueba-1", request).repetida());
    }

    @Test
    void duplicadoConcurrenteConOtrosDatosEsConflicto() {
        when(repository.buscarPorId(id)).thenReturn(Optional.empty(), Optional.of(original()));
        when(repository.insertar(any())).thenThrow(new DuplicateKeyException("duplicada"));
        assertEquals("IDEMPOTENCY_KEY_REUTILIZADA", assertThrows(ApiException.class,
            () -> service.crear("prueba-1", request("102", "25", EstadoHabitacion.DISPONIBLE))).codigo());
    }

    @Test
    void otroIdConElMismoNumeroEsConflicto() {
        when(repository.insertar(any())).thenThrow(new DuplicateKeyException("duplicada"));
        var otra = new Habitacion("b".repeat(64), original().datos(), original().creacion(), false);
        when(repository.buscarPorNumero("101")).thenReturn(Optional.of(otra));
        assertEquals("NUMERO_DUPLICADO", assertThrows(ApiException.class,
            () -> service.crear("prueba-1", request)).codigo());
    }

    @Test
    void resultadoConcurrenteAunNoVisiblePermiteReintentar() {
        when(repository.insertar(any())).thenThrow(new DuplicateKeyException("duplicada"));
        assertThrows(DataAccessResourceFailureException.class, () -> service.crear("prueba-1", request));
    }

    @Test
    void repeticionTrasActualizarOEliminarDevuelveElOriginalSinResucitar() {
        var modificados = request("101", "40", EstadoHabitacion.MANTENIMIENTO).validarYNormalizar();
        when(repository.buscarPorId(id)).thenReturn(Optional.of(
            new Habitacion(id, modificados, original().creacion(), true)));
        var resultado = service.crear("prueba-1", request);
        assertTrue(resultado.repetida());
        assertEquals(EstadoHabitacion.DISPONIBLE, resultado.habitacion().estado());
        assertEquals(new BigDecimal("25.00"), resultado.habitacion().precioNoche());
        verify(repository, never()).insertar(any());
        verify(repository, never()).actualizar(any(), any());
    }

    @Test
    void solicitudesSimultaneasProducenUnaSolaCreacion() throws Exception {
        // Doble atómico: prueba las carreras del servicio, no reemplaza una prueba de MongoDB real.
        int cantidad = 8;
        var almacen = new ConcurrentHashMap<String, Habitacion>();
        var barrera = new CyclicBarrier(cantidad);
        when(repository.buscarPorId(id)).thenAnswer(invocation -> Optional.ofNullable(almacen.get(id)));
        when(repository.insertar(any())).thenAnswer(invocation -> {
            Habitacion nueva = invocation.getArgument(0);
            barrera.await(5, TimeUnit.SECONDS);
            if (almacen.putIfAbsent(nueva.id(), nueva) != null) {
                throw new DuplicateKeyException("duplicada");
            }
            return nueva;
        });
        try (var ejecutor = Executors.newFixedThreadPool(cantidad)) {
            var futuros = java.util.stream.IntStream.range(0, cantidad)
                .mapToObj(i -> ejecutor.submit(() -> service.crear("prueba-1", request))).toList();
            int nuevas = 0;
            for (var futuro : futuros) {
                var resultado = futuro.get(10, TimeUnit.SECONDS);
                assertEquals(id, resultado.habitacion().id());
                if (!resultado.repetida()) { nuevas++; }
            }
            assertEquals(1, nuevas);
            assertEquals(1, almacen.size());
        }
    }

    @Test
    void putRepetidoEstableceLosMismosDatos() {
        when(repository.buscarPorId(id)).thenReturn(Optional.of(original()));
        when(repository.actualizar(eq(id), any())).thenAnswer(invocation ->
            Optional.of(new Habitacion(id, invocation.getArgument(1), original().creacion(), false)));
        assertEquals(service.actualizar(id, request), service.actualizar(id, request));
        verify(repository, times(2)).actualizar(id, original().datos());
    }

    @Test
    void noPermiteCambiarElNumeroNiResucitarUnaBaja() {
        when(repository.buscarPorId(id)).thenReturn(Optional.of(original()));
        assertEquals("NUMERO_INMUTABLE", assertThrows(ApiException.class,
            () -> service.actualizar(id, request("102", "25", EstadoHabitacion.DISPONIBLE))).codigo());
        when(repository.buscarPorId(id)).thenReturn(Optional.of(
            new Habitacion(id, original().datos(), original().creacion(), true)));
        assertEquals(404, assertThrows(ApiException.class, () -> service.actualizar(id, request)).status().value());
        verify(repository, never()).actualizar(any(), any());
    }

    @Test
    void eliminacionConcurrenteConPutNoRecreaElDocumento() {
        when(repository.buscarPorId(id)).thenReturn(Optional.of(original()));
        when(repository.actualizar(eq(id), any())).thenReturn(Optional.empty());
        assertEquals(404, assertThrows(ApiException.class, () -> service.actualizar(id, request)).status().value());
    }

    @Test
    void deleteRepetidoNoNecesitaQueExista() {
        service.eliminar(id);
        service.eliminar(id);
        verify(repository, times(2)).eliminar(id);
        verify(repository, never()).buscarPorId(any());
    }

    @Test
    void listaConFiltrosYPaginacion() {
        when(repository.listar(EstadoHabitacion.DISPONIBLE, 1, 10)).thenReturn(List.of(original()));
        when(repository.contar(EstadoHabitacion.DISPONIBLE)).thenReturn(11L);
        var pagina = service.listar(EstadoHabitacion.DISPONIBLE, 1, 10);
        assertEquals(11, pagina.total());
        assertEquals(1, pagina.items().size());
        assertEquals(1, pagina.pagina());
    }

    @ParameterizedTest
    @CsvSource({"-1,20", "10001,20", "0,0", "0,101"})
    void limitaLaPaginacion(int pagina, int tamano) {
        assertThrows(ApiException.class, () -> service.listar(null, pagina, tamano));
        verifyNoInteractions(repository);
    }

    @Test
    void validaIdYCuerpoAntesDeConsultar() {
        assertThrows(ApiException.class, () -> service.obtener("invalido"));
        assertThrows(ApiException.class, () -> service.eliminar("invalido"));
        assertThrows(ApiException.class, () -> service.crear("prueba-1", null));
        verifyNoInteractions(repository);
    }
}
