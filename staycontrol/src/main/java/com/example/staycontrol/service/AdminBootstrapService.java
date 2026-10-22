package com.example.staycontrol.service;

import com.example.staycontrol.dto.LoginRequest;
import com.example.staycontrol.exception.ApiException;
import com.example.staycontrol.model.Usuario;
import com.example.staycontrol.repository.UsuarioRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AdminBootstrapService {
    private final UsuarioRepository repository;
    private final PasswordEncoder encoder;

    public AdminBootstrapService(UsuarioRepository repository, PasswordEncoder encoder) {
        this.repository = repository;
        this.encoder = encoder;
    }

    public void crearSiNoExiste(String email, String password) {
        String normalizado = LoginRequest.normalizarEmail(email);
        if (password == null || password.isBlank() || password.length() < 12 || password.length() > 128) {
            throw ApiException.invalida("La contraseña inicial debe tener entre 12 y 128 caracteres.");
        }
        if (repository.buscar(normalizado).isPresent()) { return; }
        try {
            repository.insertar(new Usuario(normalizado, encoder.encode(password)));
        } catch (DuplicateKeyException exception) {
            // Otro arranque ganó la carrera. Nunca reiniciar la contraseña de un usuario existente.
            if (repository.buscar(normalizado).isEmpty()) { throw exception; }
        }
    }
}
