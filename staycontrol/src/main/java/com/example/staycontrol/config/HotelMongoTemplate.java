package com.example.staycontrol.config;

import com.example.staycontrol.model.*;
import com.example.staycontrol.dto.LoginRequest;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;

/** Usuarios globales; las cuatro colecciones de negocio se resuelven SOLO desde la sesión. */
public class HotelMongoTemplate extends MongoTemplate {
    private static final Set<Class<?>> NEGOCIO = Set.of(Habitacion.class, Reserva.class,
        TareaLimpieza.class, MovimientoFinanciero.class);
    private final String propietarioHistorico;

    public HotelMongoTemplate(MongoDatabaseFactory factory, MongoConverter converter, String propietarioHistorico) {
        super(factory, converter);
        this.propietarioHistorico = propietarioHistorico == null || propietarioHistorico.isBlank()
            ? "" : LoginRequest.normalizarEmail(propietarioHistorico);
    }

    @Override public String getCollectionName(Class<?> tipo) {
        String base = super.getCollectionName(tipo);
        if (tipo == Usuario.class) { return base; }
        if (!NEGOCIO.contains(tipo)) { throw new IllegalArgumentException("Entidad sin ámbito de hotel definido."); }
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new AuthenticationCredentialsNotFoundException("Se requiere una sesión para acceder al hotel.");
        }
        String propietario = LoginRequest.normalizarEmail(auth.getName());
        // Compatibilidad explícita: nunca inferir quién es propietario de los datos anteriores.
        if (propietario.equals(propietarioHistorico)) { return base; }
        try {
            String hotel = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(("staycontrol:hotel:v1:" + propietario).getBytes(StandardCharsets.UTF_8)));
            return "hotel_" + hotel + "_" + base;
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 no está disponible.");
        }
    }
}
