package com.example.staycontrol.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.ServerApi;
import com.mongodb.ServerApiVersion;
import com.mongodb.WriteConcern;
import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.example.staycontrol.service.MongoConnectionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration(proxyBeanMethods = false)
public class MongoConfig {

    @Bean
    public org.springframework.data.mongodb.core.MongoTemplate mongoTemplate(
        org.springframework.data.mongodb.MongoDatabaseFactory factory,
        org.springframework.data.mongodb.core.convert.MongoConverter converter,
        @Value("${app.hotel.legacy-owner-email:}") String propietarioHistorico) {
        // Comparte exactamente la misma factory que el gestor de transacciones.
        return new HotelMongoTemplate(factory, converter, propietarioHistorico);
    }

    @Bean(destroyMethod = "close")
    public MongoClient mongoClient(@Value("${spring.mongodb.uri}") String uri) {
        if (uri == null || uri.isBlank() || uri.contains("<db_password>")) {
            throw new IllegalStateException(
                "Completa MONGODB_URI en .env con la conexión a MongoDB local o Atlas."
            );
        }

        ConnectionString connectionString;
        try {
            connectionString = new ConnectionString(uri);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                "MONGODB_URI no es válida. Revisa la URI y la codificación de la contraseña."
            );
        }

        MongoClientSettings settings = MongoClientSettings.builder()
            .applyConnectionString(connectionString)
            .writeConcern(WriteConcern.MAJORITY.withWTimeout(5, TimeUnit.SECONDS))
            .readConcern(ReadConcern.MAJORITY)
            .readPreference(ReadPreference.primary())
            .serverApi(ServerApi.builder().version(ServerApiVersion.V1).build())
            .applyToClusterSettings(builder -> builder.serverSelectionTimeout(5, TimeUnit.SECONDS))
            .applyToSocketSettings(builder -> builder
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS))
            .build();

        // Un único cliente compartido; Spring lo cierra al detener la aplicación.
        return MongoClients.create(settings);
    }

    @Bean
    @ConditionalOnProperty(name = "app.mongodb.verify-on-startup", havingValue = "true", matchIfMissing = true)
    public ApplicationRunner verifyMongoConnection(MongoConnectionService connectionService) {
        return args -> connectionService.verifyConnection();
    }
}
