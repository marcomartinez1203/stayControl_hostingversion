package com.example.staycontrol.controller;

import com.mongodb.client.MongoClient;
import com.example.staycontrol.repository.HabitacionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

// Verifica las vistas empaquetadas sin depender de file:./frontend/.
@SpringBootTest(properties = {
    "spring.web.resources.static-locations=classpath:/static/",
    "spring.mongodb.uri=mongodb://127.0.0.1:27017/staycontrol_test",
    "spring.mongodb.database=staycontrol_test",
    "app.mongodb.verify-on-startup=false",
    "app.auth.bootstrap.enabled=false"
})
class ViewControllerTests {

    @MockitoBean
    private MongoClient mongoClient;

    @MockitoBean
    private HabitacionRepository habitacionRepository;

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @ParameterizedTest
    @CsvSource({
        "/, login",
        "/login, login",
        "/login.html, login",
        "/pages/login.html, login",
        "/dashboard, dashboard",
        "/dashboard.html, dashboard",
        "/pages/dashboard.html, dashboard",
        "/habitaciones, habitaciones",
        "/habitaciones.html, habitaciones",
        "/pages/habitaciones.html, habitaciones",
        "/reservas, reservas",
        "/reservas.html, reservas",
        "/pages/reservas.html, reservas",
        "/finanzas, finanzas",
        "/finanzas.html, finanzas",
        "/pages/finanzas.html, finanzas",
        "/limpieza, limpieza",
        "/limpieza.html, limpieza",
        "/pages/limpieza.html, limpieza"
    })
    void routesRedirectToTheirViews(String route, String view) throws Exception {
        mvc.perform(get(route))
            .andExpect(status().isFound())
            .andExpect(redirectedUrl("/views/" + view + ".html"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "login", "dashboard", "habitaciones", "reservas", "finanzas", "limpieza"
    })
    void servesPackagedHtmlCssAndJavaScript(String view) throws Exception {
        assertResource("/views/" + view + ".html");
        assertResource("/assets/css/" + view + ".css");
        assertResource("/controllers/" + view + "Controller.js");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/models/Habitacion.js",
        "/models/TareaLimpieza.js",
        "/services/apiService.js",
        "/services/habitacionesService.js",
        "/services/limpiezaService.js"
    })
    void servesModelsAndServices(String path) throws Exception {
        assertResource(path);
    }

    private void assertResource(String path) throws Exception {
        byte[] expected;
        try (var stream = new ClassPathResource("static" + path).getInputStream()) {
            expected = stream.readAllBytes();
        }
        mvc.perform(get(path))
            .andExpect(status().isOk())
            .andExpect(content().bytes(expected));
    }
}
