package com.example.staycontrol.service;

import com.example.staycontrol.config.SecurityConfig;
import com.example.staycontrol.exception.ApiException;
import com.example.staycontrol.model.Usuario;
import com.example.staycontrol.dto.LoginRequest;
import com.example.staycontrol.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AdminBootstrapServiceTests {
    private final UsuarioRepository repository = mock(UsuarioRepository.class);
    private final org.springframework.security.crypto.password.PasswordEncoder encoder = new SecurityConfig().passwordEncoder();
    private final AdminBootstrapService service = new AdminBootstrapService(repository, encoder);
    private static final String PASSWORD = "Solo-pruebas!42";

    @Test void guardaUnHashConSalYNormalizaEmailSinExponerCredenciales() {
        service.crearSiNoExiste(" ADMIN@example.test ", PASSWORD);
        var capture = ArgumentCaptor.forClass(Usuario.class);
        verify(repository).insertar(capture.capture());
        var usuario = capture.getValue();
        assertEquals("admin@example.test", usuario.email());
        assertNotEquals(PASSWORD, usuario.passwordHash());
        assertTrue(encoder.matches(PASSWORD, usuario.passwordHash()));
        assertNotEquals(encoder.encode(PASSWORD), usuario.passwordHash());
        assertFalse(usuario.toString().contains(usuario.passwordHash()));
        assertFalse(new LoginRequest(usuario.email(), PASSWORD).toString().contains(PASSWORD));
    }

    @Test void unReinicioNoCambiaLaContrasenaExistente() {
        when(repository.buscar("admin@example.test")).thenReturn(Optional.of(new Usuario("admin@example.test", "hash-original")));
        service.crearSiNoExiste("admin@example.test", PASSWORD);
        service.crearSiNoExiste("admin@example.test", "Otra-prueba!42");
        verify(repository, never()).insertar(any());
    }

    @Test void laCarreraDeDosArranquesNoSobrescribeElGanador() {
        when(repository.buscar("admin@example.test")).thenReturn(Optional.empty(), Optional.of(new Usuario("admin@example.test", "hash")));
        when(repository.insertar(any())).thenThrow(new DuplicateKeyException("duplicado"));
        assertDoesNotThrow(() -> service.crearSiNoExiste("admin@example.test", PASSWORD));
        verify(repository, times(1)).insertar(any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"corta", "            "})
    void rechazaContrasenasInicialesDebiles(String password) {
        assertThrows(ApiException.class, () -> service.crearSiNoExiste("admin@example.test", password));
        verifyNoInteractions(repository);
    }

    @Test void noRecortaLosEspaciosDeLaContrasena() {
        String password = "  Solo-pruebas!42  ";
        service.crearSiNoExiste("admin@example.test", password);
        var capture = ArgumentCaptor.forClass(Usuario.class);
        verify(repository).insertar(capture.capture());
        assertTrue(encoder.matches(password, capture.getValue().passwordHash()));
        assertFalse(encoder.matches(password.strip(), capture.getValue().passwordHash()));
    }
}
