package com.example.staycontrol.config;

import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class HabitacionConfig {
    // Los índices se preparan antes de la primera escritura de cada hotel.
    // En el arranque no existe sesión de propietario y no debe elegirse un hotel implícito.
}
