package com.example.staycontrol.config;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MongoConfigTests {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
        " ",
        "mongodb+srv://usuario:<db_password>@example.mongodb.net/"
    })
    void rejectsMissingCredentialsBeforeCreatingAClient(String uri) {
        var exception = assertThrows(IllegalStateException.class,
            () -> new MongoConfig().mongoClient(uri));
        assertTrue(exception.getMessage().contains("Completa MONGODB_URI"));
    }
}
