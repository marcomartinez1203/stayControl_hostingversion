package com.example.staycontrol.repository;

import com.example.staycontrol.model.DatosHabitacion;
import com.example.staycontrol.model.EstadoHabitacion;
import com.example.staycontrol.model.Habitacion;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexDefinition;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.UpdateDefinition;

import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HabitacionRepositoryTests {
    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private final HabitacionRepository repository = new HabitacionRepository(mongo);
    private final String id = "a".repeat(64);
    private final DatosHabitacion datos = new DatosHabitacion("101", "Individual", 1,
        new BigDecimal("25.00"), EstadoHabitacion.DISPONIBLE);

    @Test
    void creaUnIndiceUnicoParaElNumero() {
        var indices = mock(IndexOperations.class);
        when(mongo.indexOps(Habitacion.class)).thenReturn(indices);
        repository.prepararIndices();
        var index = ArgumentCaptor.forClass(IndexDefinition.class);
        verify(indices).createIndex(index.capture());
        assertEquals(new Document("datos.numero", 1), index.getValue().getIndexKeys());
        assertEquals(true, index.getValue().getIndexOptions().getBoolean("unique"));
    }

    @Test
    void insertaDatosYOriginalJuntosSinSaveNiUpsert() {
        var habitacion = new Habitacion(id, datos, datos, false);
        repository.insertar(habitacion);
        verify(mongo).insert(habitacion);
        verifyNoMoreInteractions(mongo);
    }

    @Test
    void filtraBajasYEstadoConOrdenYPaginacionAcotada() {
        repository.listar(EstadoHabitacion.DISPONIBLE, 2, 10);
        var query = ArgumentCaptor.forClass(Query.class);
        verify(mongo).find(query.capture(), eq(Habitacion.class));
        assertEquals(false, query.getValue().getQueryObject().getBoolean("eliminada"));
        assertEquals(EstadoHabitacion.DISPONIBLE, query.getValue().getQueryObject().get("datos.estado"));
        assertEquals(20, query.getValue().getSkip());
        assertEquals(10, query.getValue().getLimit());
        assertEquals(new Document("datos.numero", 1), query.getValue().getSortObject());
    }

    @Test
    void actualizaSoloDatosSinTocarIdempotenciaNiResucitar() {
        repository.actualizar(id, datos);
        var query = ArgumentCaptor.forClass(Query.class);
        var update = ArgumentCaptor.forClass(UpdateDefinition.class);
        var options = ArgumentCaptor.forClass(FindAndModifyOptions.class);
        verify(mongo).findAndModify(query.capture(), update.capture(), options.capture(), eq(Habitacion.class));
        assertEquals(new Document("_id", id).append("eliminada", false), query.getValue().getQueryObject());
        assertEquals(new Document("$set", new Document("datos", datos)), update.getValue().getUpdateObject());
        assertFalse(options.getValue().isUpsert());
        assertTrue(options.getValue().isReturnNew());
    }

    @Test
    void deleteSoloMarcaLaBajaSinBorrarElOriginal() {
        repository.eliminar(id);
        var query = ArgumentCaptor.forClass(Query.class);
        var update = ArgumentCaptor.forClass(UpdateDefinition.class);
        verify(mongo).updateFirst(query.capture(), update.capture(), eq(Habitacion.class));
        assertEquals(new Document("_id", id).append("eliminada", false), query.getValue().getQueryObject());
        assertEquals(new Document("$set", new Document("eliminada", true)), update.getValue().getUpdateObject());
        verifyNoMoreInteractions(mongo);
    }
}
