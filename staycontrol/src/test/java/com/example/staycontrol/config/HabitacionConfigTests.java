package com.example.staycontrol.config;

import com.example.staycontrol.repository.HabitacionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class HabitacionConfigTests {
    @Test
    void iniciaSinPrepararIndicesDeUnHotelSinSesion() {
        var repository = mock(HabitacionRepository.class);
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(HabitacionRepository.class, () -> repository);
            context.register(HabitacionConfig.class);
            context.refresh();

            assertNotNull(context.getBean(HabitacionConfig.class));
            // Los índices se crean al escribir en el hotel autenticado, no al arrancar.
            verifyNoInteractions(repository);
        }
    }
}
