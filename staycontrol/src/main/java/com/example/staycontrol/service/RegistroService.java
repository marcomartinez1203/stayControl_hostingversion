package com.example.staycontrol.service;

import com.example.staycontrol.dto.LoginRequest;
import com.example.staycontrol.dto.RegistroRequest;
import com.example.staycontrol.exception.ApiException;
import com.example.staycontrol.model.Usuario;
import com.example.staycontrol.repository.UsuarioRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class RegistroService {
    public record Resultado(String email, String usuario, String rol, String nombreHotel, boolean repetido) { }
    private final UsuarioRepository repository;
    private final PasswordEncoder encoder;
    private final IdempotencyService idempotencia;

    public RegistroService(UsuarioRepository repository, PasswordEncoder encoder, IdempotencyService idempotencia) {
        this.repository = repository;
        this.encoder = encoder;
        this.idempotencia = idempotencia;
    }

    public Resultado registrar(RegistroRequest datos, String clave) {
        String email = LoginRequest.normalizarEmail(datos.email());
        String nombre = datos.nombreNormalizado();
        String hotel = datos.hotelNormalizado();
        datos.validarPassword();
        // La clave se delimita por operación y correo; no permite sobrescribir otra cuenta.
        String identidad = idempotencia.identificador("POST:/api/v1/auth/registro:" + email, clave);
        var existente = repository.buscar(email);
        if (existente.isPresent()) { return repetir(existente.get(), identidad, nombre, datos.password(), hotel); }
        try {
            repository.insertar(new Usuario(email, encoder.encode(datos.password()), nombre, "USER", identidad, hotel));
            return new Resultado(email, nombre, "USER", hotel, false);
        } catch (DuplicateKeyException exception) {
            Usuario ganador = repository.buscar(email).orElseThrow(() -> new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE, "REGISTRO_EN_PROCESO", "Reintenta con la misma Idempotency-Key."));
            return repetir(ganador, identidad, nombre, datos.password(), hotel);
        }
    }

    private Resultado repetir(Usuario usuario, String identidad, String nombre, String password, String hotel) {
        if (!identidad.equals(usuario.registroKey()) || !nombre.equals(usuario.usuario())
            || !hotel.equals(usuario.nombreHotel()) || !encoder.matches(password, usuario.passwordHash())) {
            throw ApiException.conflicto("REGISTRO_NO_DISPONIBLE", "No se puede completar el registro con estos datos.");
        }
        return new Resultado(usuario.email(), usuario.usuario(), "USER", usuario.nombreHotel(), true);
    }
}
