package com.example.staycontrol.controller;

import com.example.staycontrol.model.DatosHabitacion;
import com.example.staycontrol.model.EstadoHabitacion;
import com.example.staycontrol.model.Habitacion;
import com.example.staycontrol.repository.HabitacionRepository;
import com.example.staycontrol.service.IdempotencyService;
import com.mongodb.client.MongoClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

@SpringBootTest(properties = {
    "spring.mongodb.uri=mongodb://127.0.0.1:27017/staycontrol_test",
    "spring.mongodb.database=staycontrol_test",
    "app.mongodb.verify-on-startup=false",
    "app.auth.bootstrap.enabled=false"
})
class HabitacionControllerTests {
    @MockitoBean private MongoClient mongoClient;
    @MockitoBean private HabitacionRepository repository;
    @Autowired private WebApplicationContext context;
    private MockMvc mvc;
    private static final String URL = "/api/v1/habitaciones";
    private static final String JSON = """
        {"numero":"101","tipo":"Individual","capacidad":1,"precioNoche":25.00,"estado":"DISPONIBLE"}
        """;
    private final String id = new IdempotencyService().identificador("POST:" + URL, "prueba-1");

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity())
            .defaultRequest(get("/").with(user("operador@example.test").roles("ADMIN")).with(csrf())).build();
    }

    private Habitacion original() {
        var datos = new DatosHabitacion("101", "Individual", 1, new BigDecimal("25.00"), EstadoHabitacion.DISPONIBLE);
        return new Habitacion(id, datos, datos, false);
    }

    @Test
    void postDevuelve201LocationYDatosPublicos() throws Exception {
        when(repository.insertar(any())).thenAnswer(invocation -> invocation.getArgument(0));
        mvc.perform(post(URL).header("Idempotency-Key", "prueba-1").contentType("application/json").content(JSON))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", URL + "/" + id))
            .andExpect(header().string("Idempotency-Replayed", "false"))
            .andExpect(jsonPath("$.id").value(id))
            .andExpect(jsonPath("$.numero").value("101"))
            .andExpect(jsonPath("$.precioNoche").value(25.0))
            .andExpect(jsonPath("$.creacion").doesNotExist())
            .andExpect(jsonPath("$.eliminada").doesNotExist());
    }

    @Test
    void postRepetidoDevuelveMismoStatusCuerpoYLocation() throws Exception {
        when(repository.insertar(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var primera = mvc.perform(post(URL).header("Idempotency-Key", "prueba-1")
            .contentType("application/json").content(JSON)).andExpect(status().isCreated()).andReturn();
        when(repository.buscarPorId(id)).thenReturn(Optional.of(original()));
        mvc.perform(post(URL).header("Idempotency-Key", "prueba-1").contentType("application/json").content(JSON))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", URL + "/" + id))
            .andExpect(header().string("Idempotency-Replayed", "true"))
            .andExpect(content().json(primera.getResponse().getContentAsString()));
        verify(repository, times(1)).insertar(any());
    }

    @Test
    void postSinClaveNoEscribe() throws Exception {
        mvc.perform(post(URL).contentType("application/json").content(JSON))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));
        verify(repository, never()).insertar(any());
    }

    @Test
    void claveReutilizadaConOtrosDatosDevuelve409() throws Exception {
        when(repository.buscarPorId(id)).thenReturn(Optional.of(original()));
        mvc.perform(post(URL).header("Idempotency-Key", "prueba-1")
            .contentType("application/json").content(JSON.replace("101", "102")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.codigo").value("IDEMPOTENCY_KEY_REUTILIZADA"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{}", "null", "{", "[]",
        "{\"numero\":\"101\",\"tipo\":\"Individual\",\"capacidad\":1.5,\"precioNoche\":25,\"estado\":\"DISPONIBLE\"}",
        "{\"numero\":\"101\",\"tipo\":\"Individual\",\"capacidad\":1,\"precioNoche\":25,\"estado\":0}",
        "{\"numero\":\"101\",\"tipo\":\"Individual\",\"capacidad\":1,\"precioNoche\":25,\"estado\":\"OTRO\"}",
        "{\"numero\":\"101\",\"tipo\":\"Individual\",\"capacidad\":1,\"precioNoche\":25,\"estado\":\"DISPONIBLE\",\"extra\":true}"
    })
    void jsonInvalidoDevuelve400SinEscribir(String json) throws Exception {
        mvc.perform(post(URL).header("Idempotency-Key", "prueba-1").contentType("application/json").content(json))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));
        verify(repository, never()).insertar(any());
    }

    @Test
    void getListaPaginadaConFiltro() throws Exception {
        when(repository.listar(EstadoHabitacion.DISPONIBLE, 0, 20)).thenReturn(List.of(original()));
        when(repository.contar(EstadoHabitacion.DISPONIBLE)).thenReturn(1L);
        mvc.perform(get(URL).param("estado", "DISPONIBLE"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(id))
            .andExpect(jsonPath("$.pagina").value(0)).andExpect(jsonPath("$.tamano").value(20))
            .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void getIndividualY404() throws Exception {
        when(repository.buscarPorId(id)).thenReturn(Optional.of(original()), Optional.empty());
        mvc.perform(get(URL + "/" + id)).andExpect(status().isOk()).andExpect(jsonPath("$.numero").value("101"));
        mvc.perform(get(URL + "/" + id)).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.codigo").value("HABITACION_NO_ENCONTRADA"));
    }

    @Test
    void parametrosInvalidosDevuelven400() throws Exception {
        mvc.perform(get(URL).param("estado", "otro")).andExpect(status().isBadRequest());
        mvc.perform(get(URL).param("pagina", "abc")).andExpect(status().isBadRequest());
        mvc.perform(get(URL).param("tamano", "101")).andExpect(status().isBadRequest());
        mvc.perform(get(URL + "/invalido")).andExpect(status().isBadRequest());
    }

    @Test
    void putRepetidoDevuelve200SinCrearNiCambiarLaCreacion() throws Exception {
        when(repository.buscarPorId(id)).thenReturn(Optional.of(original()));
        when(repository.actualizar(eq(id), any())).thenReturn(Optional.of(original()));
        for (int intento = 0; intento < 2; intento++) {
            mvc.perform(put(URL + "/" + id).contentType("application/json").content(JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
        }
        verify(repository, never()).insertar(any());
    }

    @Test
    void deleteRepetidoDevuelve204SinCuerpo() throws Exception {
        for (int intento = 0; intento < 2; intento++) {
            mvc.perform(delete(URL + "/" + id)).andExpect(status().isNoContent()).andExpect(content().string(""));
        }
    }

    @Test
    void falloMongoDevuelve503SinDetallesInternos() throws Exception {
        when(repository.buscarPorId(id)).thenThrow(new DataAccessResourceFailureException("detalle-interno-privado"));
        mvc.perform(post(URL).header("Idempotency-Key", "prueba-1").contentType("application/json").content(JSON))
            .andExpect(status().isServiceUnavailable()).andExpect(header().string("Retry-After", "1"))
            .andExpect(jsonPath("$.codigo").value("BASE_NO_DISPONIBLE"))
            .andExpect(content().string(not(containsString("detalle-interno-privado"))));
    }
}
