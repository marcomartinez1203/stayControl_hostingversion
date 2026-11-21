package com.example.staycontrol.repository;

import com.example.staycontrol.model.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public class LimpiezaRepository {
    private final MongoTemplate mongo;
    private final java.util.Set<String> indicesListos = new java.util.HashSet<>();
    public LimpiezaRepository(MongoTemplate mongo) { this.mongo = mongo; }

    public synchronized void prepararIndices() {
        String coleccion = mongo.getCollectionName(TareaLimpieza.class);
        if (indicesListos.contains(coleccion)) { return; }
        mongo.indexOps(TareaLimpieza.class).createIndex(new Index().on("datos.habitacionId", Sort.Direction.ASC)
            .on("datos.fechaProgramada", Sort.Direction.ASC).on("estado", Sort.Direction.ASC).named("ix_limpieza_agenda"));
        indicesListos.add(coleccion);
    }

    public Optional<TareaLimpieza> buscar(String id) { return Optional.ofNullable(mongo.findById(id, TareaLimpieza.class)); }
    public TareaLimpieza insertar(TareaLimpieza tarea) { return mongo.insert(tarea); }

    public void generarPorSalida(String habitacionId, String tareaId, String fecha) {
        var habitacion = Query.query(Criteria.where("_id").is(habitacionId)
            .and("datos.estado").is(EstadoHabitacion.OCUPADA).and("eliminada").is(false));
        var cambio = new Update().set("datos.estado", EstadoHabitacion.LIMPIEZA)
            .set("limpiezaSalidaId", tareaId).inc("tareasLimpiezaActivas", 1);
        if (mongo.updateFirst(habitacion, cambio, Habitacion.class).getMatchedCount() != 1) {
            throw new DataIntegrityViolationException("La habitación de la estancia no está ocupada.");
        }
        insertar(new TareaLimpieza(tareaId, new DatosLimpieza(habitacionId,
            "Limpieza tras la salida del huésped", "", fecha), EstadoLimpieza.PENDIENTE));
    }

    public boolean vincularHabitacion(String id) {
        return mongo.updateFirst(Query.query(Criteria.where("_id").is(id).and("eliminada").is(false)),
            new Update().inc("tareasLimpiezaActivas", 1), Habitacion.class).getMatchedCount() == 1;
    }

    /** Debe ejecutarse en la misma transacción que la lectura del estado actual. */
    public TareaLimpieza actualizarEstado(TareaLimpieza actual, EstadoLimpieza estado) {
        if (estado.cerrada()) {
            var limpiezaSalida = Query.query(Criteria.where("_id").is(actual.datos().habitacionId())
                .and("limpiezaSalidaId").is(actual.id()));
            if (estado == EstadoLimpieza.CANCELADA && mongo.exists(limpiezaSalida, Habitacion.class)) {
                throw com.example.staycontrol.exception.ApiException.conflicto("LIMPIEZA_REQUERIDA",
                    "Completa la limpieza de salida para liberar la habitación.");
            }
            if (estado == EstadoLimpieza.COMPLETADA) {
                mongo.updateFirst(limpiezaSalida, new Update().unset("limpiezaSalidaId")
                    .set("datos.estado", EstadoHabitacion.DISPONIBLE), Habitacion.class);
            }
            var resultado = mongo.updateFirst(Query.query(Criteria.where("_id").is(actual.datos().habitacionId())
                .and("tareasLimpiezaActivas").gt(0)), new Update().inc("tareasLimpiezaActivas", -1), Habitacion.class);
            if (resultado.getMatchedCount() != 1) {
                throw new DataIntegrityViolationException("Contador de limpieza inconsistente.");
            }
        }
        var modificada = mongo.findAndModify(Query.query(Criteria.where("_id").is(actual.id())
            .and("estado").is(actual.estado())), new Update().set("estado", estado),
            FindAndModifyOptions.options().returnNew(true), TareaLimpieza.class);
        if (modificada == null) { throw new DataIntegrityViolationException("Cambio de limpieza concurrente."); }
        return modificada;
    }

    public List<TareaLimpieza> listar(EstadoLimpieza estado, String habitacionId, String fecha, int pagina, int tamano) {
        return mongo.find(filtro(estado, habitacionId, fecha).with(Sort.by("datos.fechaProgramada", "id"))
            .skip((long) pagina * tamano).limit(tamano), TareaLimpieza.class);
    }

    public long contar(EstadoLimpieza estado, String habitacionId, String fecha) {
        return mongo.count(filtro(estado, habitacionId, fecha), TareaLimpieza.class);
    }

    private Query filtro(EstadoLimpieza estado, String habitacionId, String fecha) {
        Query query = new Query();
        if (estado != null) { query.addCriteria(Criteria.where("estado").is(estado)); }
        if (habitacionId != null) { query.addCriteria(Criteria.where("datos.habitacionId").is(habitacionId)); }
        if (fecha != null) { query.addCriteria(Criteria.where("datos.fechaProgramada").is(fecha)); }
        return query;
    }
}
