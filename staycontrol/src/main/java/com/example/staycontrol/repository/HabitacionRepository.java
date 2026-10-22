package com.example.staycontrol.repository;

import com.example.staycontrol.model.DatosHabitacion;
import com.example.staycontrol.model.EstadoHabitacion;
import com.example.staycontrol.model.Habitacion;
import com.example.staycontrol.exception.ApiException;
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
import java.util.regex.Pattern;

@Repository
public class HabitacionRepository {
    private final MongoTemplate mongo;
    private final java.util.Set<String> indicesListos = new java.util.HashSet<>();

    public HabitacionRepository(MongoTemplate mongo) { this.mongo = mongo; }

    public synchronized void prepararIndices() {
        String coleccion = mongo.getCollectionName(Habitacion.class);
        if (indicesListos.contains(coleccion)) { return; }
        // _id ya es único; este segundo índice impide dos claves distintas para un mismo número.
        mongo.indexOps(Habitacion.class).createIndex(
            new Index().on("datos.numero", Sort.Direction.ASC).unique().named("uk_habitaciones_numero"));
        indicesListos.add(coleccion);
    }

    public Optional<Habitacion> buscarPorId(String id) {
        return Optional.ofNullable(mongo.findById(id, Habitacion.class));
    }

    public Optional<Habitacion> buscarPorNumero(String numero) {
        return Optional.ofNullable(mongo.findOne(
            Query.query(Criteria.where("datos.numero").is(numero)), Habitacion.class));
    }

    public Habitacion insertar(Habitacion habitacion) {
        prepararIndices();
        // insert, nunca save/upsert: un reintento no puede sobrescribir la creación original.
        return mongo.insert(habitacion);
    }

    public List<Habitacion> listar(EstadoHabitacion estado, int pagina, int tamano) {
        Query query = activas(estado).with(Sort.by("datos.numero"))
            .skip((long) pagina * tamano).limit(tamano);
        return mongo.find(query, Habitacion.class);
    }

    public long contar(EstadoHabitacion estado) {
        return mongo.count(activas(estado), Habitacion.class);
    }

    public List<Habitacion> listar(EstadoHabitacion estado, int pagina, int tamano, String buscar) {
        return mongo.find(activas(estado, buscar).with(Sort.by("datos.numero"))
            .skip((long) pagina * tamano).limit(tamano), Habitacion.class);
    }

    public long contar(EstadoHabitacion estado, String buscar) {
        return mongo.count(activas(estado, buscar), Habitacion.class);
    }

    private Query activas(EstadoHabitacion estado, String buscar) {
        // Texto literal, nunca una expresión regular proporcionada por el cliente.
        String literal = Pattern.quote(buscar);
        return activas(estado).addCriteria(new Criteria().orOperator(
            Criteria.where("datos.numero").regex(literal, "i"),
            Criteria.where("datos.tipo").regex(literal, "i")));
    }

    public Optional<Habitacion> actualizar(String id, DatosHabitacion datos) {
        var actualizada = mongo.findAndModify(sinReservas(id), new Update().set("datos", datos),
            FindAndModifyOptions.options().returnNew(true), Habitacion.class);
        if (actualizada == null) { comprobarReservas(id); }
        return Optional.ofNullable(actualizada);
    }

    public void eliminar(String id) {
        Query query = sinReservas(id).addCriteria(Criteria.where("tareasLimpiezaActivas").in(java.util.Arrays.asList(null, 0)));
        var resultado = mongo.updateFirst(query, new Update().set("eliminada", true), Habitacion.class);
        if (resultado.getMatchedCount() == 0) {
            comprobarReservas(id);
            if (mongo.exists(Query.query(Criteria.where("_id").is(id).and("tareasLimpiezaActivas").gt(0)), Habitacion.class)) {
                throw ApiException.conflicto("HABITACION_CON_LIMPIEZA", "Completa o cancela las tareas antes de eliminar la habitación.");
            }
        }
    }

    private Query sinReservas(String id) {
        return Query.query(Criteria.where("_id").is(id).and("eliminada").is(false)
            .orOperator(Criteria.where("reservasActivas").exists(false), Criteria.where("reservasActivas").is(0)));
    }

    private void comprobarReservas(String id) {
        if (mongo.exists(Query.query(Criteria.where("_id").is(id).and("reservasActivas").gt(0)), Habitacion.class)) {
            throw ApiException.conflicto("HABITACION_CON_RESERVAS", "Cancela o finaliza las reservas antes de editar o eliminar la habitación.");
        }
    }

    private Query activas(EstadoHabitacion estado) {
        Criteria criteria = Criteria.where("eliminada").is(false);
        if (estado != null) { criteria.and("datos.estado").is(estado); }
        return Query.query(criteria);
    }
}
