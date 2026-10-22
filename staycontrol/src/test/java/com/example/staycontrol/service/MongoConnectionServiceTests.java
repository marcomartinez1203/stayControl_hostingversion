package com.example.staycontrol.service;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.mongodb.core.MongoTemplate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MongoConnectionServiceTests {

    private final MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    private final MongoConnectionService service = new MongoConnectionService(mongoTemplate);

    @Test
    void verifiesConnectionUsingOnlyPing() {
        when(mongoTemplate.executeCommand(new Document("ping", 1)))
            .thenReturn(new Document("ok", 1.0));

        assertDoesNotThrow(service::verifyConnection);
        verify(mongoTemplate).executeCommand(new Document("ping", 1));
    }

    @Test
    void failsWhenMongoDoesNotConfirmPing() {
        when(mongoTemplate.executeCommand(new Document("ping", 1)))
            .thenReturn(new Document("ok", 0));

        assertThrows(IllegalStateException.class, service::verifyConnection);
    }

    @Test
    void reportsConnectionFailureWithoutExposingDriverDetails() {
        when(mongoTemplate.executeCommand(new Document("ping", 1)))
            .thenThrow(new DataAccessResourceFailureException("detalle interno"));

        var exception = assertThrows(IllegalStateException.class, service::verifyConnection);
        assertTrue(exception.getMessage().contains("No se pudo conectar"));
        assertNull(exception.getCause());
    }
}
