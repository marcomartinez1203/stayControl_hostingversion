package com.example.staycontrol.config;

import com.example.staycontrol.service.AdminBootstrapService;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AdminBootstrapConfig {
    @Bean
    @ConditionalOnProperty(name = "app.auth.bootstrap.enabled", havingValue = "true")
    public InitializingBean crearAdminInicial(AdminBootstrapService service,
        @Value("${app.auth.bootstrap.email:}") String email,
        @Value("${app.auth.bootstrap.password:}") String password) {
        return () -> service.crearSiNoExiste(email, password);
    }
}
