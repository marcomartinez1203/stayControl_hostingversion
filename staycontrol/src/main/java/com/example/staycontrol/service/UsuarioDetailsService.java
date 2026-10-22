package com.example.staycontrol.service;

import com.example.staycontrol.repository.UsuarioRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UsuarioDetailsService implements UserDetailsService {
    private final UsuarioRepository repository;
    public UsuarioDetailsService(UsuarioRepository repository) { this.repository = repository; }

    @Override
    public UserDetails loadUserByUsername(String email) {
        var usuario = repository.buscar(email)
            .orElseThrow(() -> new UsernameNotFoundException("Credenciales inválidas."));
        return User.withUsername(usuario.email()).password(usuario.passwordHash()).roles(usuario.rolEfectivo()).build();
    }
}
