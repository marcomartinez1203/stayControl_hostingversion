package com.example.staycontrol.repository;

import com.example.staycontrol.model.DatosHabitacion;
import com.example.staycontrol.model.EstadoHabitacion;
import com.example.staycontrol.model.Habitacion;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class HabitacionMappingTests {
    @Test
    void persisteDecimalesYLaCreacionOriginalSinDependerDeMemoria() throws Exception {
        var conversions = MongoCustomConversions.create(adapter -> { });
        var context = new MongoMappingContext();
        context.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
        context.afterPropertiesSet();
        var converter = new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, context);
        converter.setCustomConversions(conversions);
        converter.afterPropertiesSet();

        var originales = new DatosHabitacion("101", "Individual", 1, new BigDecimal("25.00"), EstadoHabitacion.DISPONIBLE);
        var actuales = new DatosHabitacion("101", "Individual", 1, new BigDecimal("40.00"), EstadoHabitacion.LIMPIEZA);
        var habitacion = new Habitacion("a".repeat(64), actuales, originales, true);
        var bson = new Document();
        converter.write(habitacion, bson);

        assertEquals(habitacion.id(), bson.getString("_id"));
        assertInstanceOf(Decimal128.class, bson.get("datos", Document.class).get("precioNoche"));
        assertInstanceOf(Decimal128.class, bson.get("creacion", Document.class).get("precioNoche"));
        assertEquals(habitacion, converter.read(Habitacion.class, bson));
        assertTrue(converter.read(Habitacion.class, bson).creacion().coincideCon(originales));
    }
}
