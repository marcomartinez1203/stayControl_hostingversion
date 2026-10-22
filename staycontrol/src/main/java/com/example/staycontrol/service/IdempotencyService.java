package com.example.staycontrol.service;

import com.example.staycontrol.exception.ApiException;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class IdempotencyService {
    /** Identidad estable por operación. La clave original no se guarda ni se registra. */
    public String identificador(String operacion, String clave) {
        if (clave == null || !clave.matches("[A-Za-z0-9._:-]{1,128}")) {
            throw ApiException.invalida("Idempotency-Key es obligatorio: 1 a 128 letras, dígitos, puntos, guiones, dos puntos o guiones bajos.");
        }
        try {
            byte[] bytes = (operacion + "\n" + clave).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 no está disponible.");
        }
    }
}
