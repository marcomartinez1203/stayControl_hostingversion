package com.example.staycontrol.controller;

import com.example.staycontrol.model.Usuario;
import com.example.staycontrol.repository.HabitacionRepository;
import com.example.staycontrol.repository.UsuarioRepository;
import com.example.staycontrol.service.LoginAttemptLimiter;
import com.example.staycontrol.exception.ApiException;
import com.mongodb.client.MongoClient;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.mongodb.uri=mongodb://127.0.0.1:27017/staycontrol_test",
    "spring.mongodb.database=staycontrol_test", "app.mongodb.verify-on-startup=false",
    "app.auth.bootstrap.enabled=false"
})
class AuthControllerTests {
    @MockitoBean private MongoClient mongoClient;
    @MockitoBean private HabitacionRepository habitaciones;
    @MockitoBean private UsuarioRepository usuarios;
    @MockitoBean private LoginAttemptLimiter limiter;
    @Autowired private PasswordEncoder encoder;
    @Autowired private WebApplicationContext context;
    private MockMvc mvc;
    private static final String EMAIL = "operador@example.test";
    private static final String PASSWORD = "Solo-pruebas!42";
    private static final String JSON = "{\"email\":\" OPERADOR@example.test \",\"password\":\"" + PASSWORD + "\"}";
    private record CsrfSession(MockHttpSession session, String header, String token) { }

    @BeforeEach void preparar() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private CsrfSession token(MockHttpSession session) throws Exception {
        var request = get("/api/v1/auth/csrf");
        if (session != null) { request.session(session); }
        var result = mvc.perform(request).andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", containsString("no-store"))).andReturn();
        String json = result.getResponse().getContentAsString();
        return new CsrfSession((MockHttpSession) result.getRequest().getSession(),
            JsonPath.read(json, "$.headerName"), JsonPath.read(json, "$.token"));
    }

    private void usuarioExiste() {
        when(usuarios.buscar(EMAIL)).thenReturn(Optional.of(new Usuario(EMAIL, encoder.encode(PASSWORD))));
    }

    private MvcResult login(CsrfSession csrf) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").session(csrf.session()).header(csrf.header(), csrf.token())
            .contentType("application/json").content(JSON)).andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value(EMAIL))
            .andExpect(jsonPath("$.password").doesNotExist())
            .andExpect(jsonPath("$.passwordHash").doesNotExist()).andReturn();
    }

    @Test void csrfEsPublicoPeroSesionYHabitacionesNo() throws Exception {
        assertFalse(token(null).token().isBlank());
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/habitaciones")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/habitaciones").with(csrf()).contentType("application/json").content("{}"))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(usuarios, habitaciones);
    }

    @Test void noAceptaLoginSinCsrf() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType("application/json").content(JSON))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
        verifyNoInteractions(usuarios);
    }

    @Test void loginRotaSesionPersisteAutenticacionYEliminaHashDeLaSesion() throws Exception {
        usuarioExiste();
        var csrf = token(null);
        String anterior = csrf.session().getId();
        login(csrf);
        assertNotEquals(anterior, csrf.session().getId());
        mvc.perform(get("/api/v1/auth/me").session(csrf.session())).andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value(EMAIL));
        var security = (SecurityContext) csrf.session().getAttribute("SPRING_SECURITY_CONTEXT");
        assertNull(((UserDetails) security.getAuthentication().getPrincipal()).getPassword());
        assertNull(security.getAuthentication().getCredentials());
        mvc.perform(get("/api/v1/habitaciones").session(csrf.session())).andExpect(status().isOk());
    }

    @Test void loginRepetidoConLaMismaSesionNoVuelveARotarla() throws Exception {
        usuarioExiste();
        var primera = token(null);
        login(primera);
        String idSesion = primera.session().getId();
        login(token(primera.session()));
        assertEquals(idSesion, primera.session().getId());
        verify(usuarios, never()).insertar(any());
    }

    @Test void tokenAnteriorAlLoginYaNoAutorizaEscrituras() throws Exception {
        usuarioExiste();
        var anterior = token(null);
        login(anterior);
        mvc.perform(post("/api/v1/auth/logout").session(anterior.session()).header(anterior.header(), anterior.token()))
            .andExpect(status().isForbidden());
    }

    @Test void logoutInvalidaLaSesionYSePuedeRepetirConCsrfNuevo() throws Exception {
        usuarioExiste();
        var csrf = token(null);
        login(csrf);
        var salida = token(csrf.session());
        mvc.perform(post("/api/v1/auth/logout").session(salida.session()).header(salida.header(), salida.token()))
            .andExpect(status().isNoContent()).andExpect(cookie().maxAge("JSESSIONID", 0));
        assertTrue(salida.session().isInvalid());
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
        var anonimo = token(null);
        mvc.perform(post("/api/v1/auth/logout").session(anonimo.session()).header(anonimo.header(), anonimo.token()))
            .andExpect(status().isNoContent());
    }

    @Test void erroresNoRevelanSiExisteElEmail() throws Exception {
        var noExiste = mvc.perform(post("/api/v1/auth/login").with(csrf())
            .contentType("application/json").content(JSON)).andExpect(status().isUnauthorized()).andReturn();
        usuarioExiste();
        mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType("application/json")
            .content(JSON.replace(PASSWORD, "incorrecta"))).andExpect(status().isUnauthorized())
            .andExpect(content().json(noExiste.getResponse().getContentAsString()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "{", "{\"email\":\"invalido\",\"password\":\"algo\"}",
        "{\"email\":\"a@example.test\",\"password\":\"algo\",\"role\":\"ADMIN\"}"})
    void validaElJsonSinConsultarMongo(String json) throws Exception {
        mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType("application/json").content(json))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(usuarios);
    }

    @Test void limitaIntentosAntesDeConsultarMongo() throws Exception {
        doThrow(new ApiException(HttpStatus.TOO_MANY_REQUESTS, "DEMASIADOS_INTENTOS", "Espera."))
            .when(limiter).comprobar(anyString());
        mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType("application/json").content(JSON))
            .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "60"));
        verifyNoInteractions(usuarios);
    }

    @Test void noExponeErroresDeMongoNiPermiteRolesInsuficientes() throws Exception {
        when(usuarios.buscar(EMAIL)).thenThrow(new DataAccessResourceFailureException("detalle-secreto"));
        mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType("application/json").content(JSON))
            .andExpect(status().isServiceUnavailable()).andExpect(content().string(not(containsString("detalle-secreto"))));
        mvc.perform(get("/api/v1/habitaciones").with(user(EMAIL).roles("USER"))).andExpect(status().isForbidden());
    }
}
