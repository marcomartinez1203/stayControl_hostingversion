package com.example.staycontrol.controller.api;

import com.example.staycontrol.exception.ApiException;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.transaction.TransactionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingServletRequestParameterException;

@RestControllerAdvice(basePackages = "com.example.staycontrol.controller.api")
public class ApiExceptionHandler {
    public record ErrorResponse(String codigo, String mensaje) { }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> negocio(ApiException exception) {
        return ResponseEntity.status(exception.status())
            .headers(headers -> { if (exception.status().value() == 429) { headers.set("Retry-After", "60"); } })
            .body(new ErrorResponse(exception.codigo(), exception.getMessage()));
    }

    @ExceptionHandler(InternalAuthenticationServiceException.class)
    public ResponseEntity<ErrorResponse> autenticacionNoDisponible(InternalAuthenticationServiceException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(new ErrorResponse("AUTH_NO_DISPONIBLE", "No se pudo comprobar la sesión. Inténtalo más tarde."));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> credencialesInvalidas(AuthenticationException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(new ErrorResponse("CREDENCIALES_INVALIDAS", "Email o contraseña incorrectos."));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
        MissingServletRequestParameterException.class})
    public ResponseEntity<ErrorResponse> entradaInvalida(Exception exception) {
        return ResponseEntity.badRequest().body(new ErrorResponse("SOLICITUD_INVALIDA",
            "Revisa el JSON, los tipos de datos y los estados permitidos."));
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    public ResponseEntity<ErrorResponse> baseNoDisponible(Exception exception) {
        // No exponer URI, documentos, consultas ni detalles del controlador de MongoDB.
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).header("Retry-After", "1")
            .body(new ErrorResponse("BASE_NO_DISPONIBLE",
                "No se pudo completar la operación. Reintenta con la misma Idempotency-Key si era un POST."));
    }
}
