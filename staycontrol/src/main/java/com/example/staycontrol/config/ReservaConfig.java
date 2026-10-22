package com.example.staycontrol.config;

import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.TransactionOptions;
import com.mongodb.WriteConcern;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import java.util.concurrent.TimeUnit;

@Configuration(proxyBeanMethods = false)
public class ReservaConfig {
    @Bean
    public MongoTransactionManager reservasTransactionManager(MongoDatabaseFactory factory) {
        return new MongoTransactionManager(factory, TransactionOptions.builder()
            .readConcern(ReadConcern.SNAPSHOT).readPreference(ReadPreference.primary())
            .writeConcern(WriteConcern.MAJORITY.withWTimeout(5, TimeUnit.SECONDS))
            .maxCommitTime(5L, TimeUnit.SECONDS).build());
    }
}
