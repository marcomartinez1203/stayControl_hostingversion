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
import java.util.regex.Pattern;

@Repository
public class ReservaRepository {
    private final MongoTemplate mongo;
    private final java.util.Set<String> indicesListos = new java.util.HashSet<>();
    public ReservaRepository(MongoTemplate mongo) { this.mongo = mongo; }

    /** Se llama antes de la primera transacción, nunca dentro de ella. */
    public synchronized void prepararIndices() {
        String coleccion = mongo.getCollectionName(Reserva.class);
        if (indicesListos.contains(coleccion)) { return; }
        mongo.indexOps(Reserva.class).createIndex(new Index().on("datos.habitacionId", Sort.Direction.ASC)
            .on("estado", Sort.Direction.ASC).on("datos.fechaEntrada", Sort.Direction.ASC)
            .on("datos.fechaSalida", Sort.Direction.ASC).named("ix_reservas_disponibilidad"));
        indicesListos.add(coleccion);
    }

    public Optional<Reserva> buscar(String id) { return Optional.ofNullable(mongo.findById(id, Reserva.class)); }
    public Reserva insertar(Reserva reserva) { return mongo.insert(reserva); }

    public Optional<Habitacion> consultarHabitacion(String id) {
        return Optional.ofNullable(mongo.findOne(
            Query.query(Criteria.where("_id").is(id).and("eliminada").is(false)), Habitacion.class));
    }

    public Optional<Habitacion> bloquearHabitacion(String id) {
        // Una escritura real sobre el mismo documento serializa reservas de esa habitación.
        return Optional.ofNullable(mongo.findAndModify(
            Query.query(Criteria.where("_id").is(id).and("eliminada").is(false)),
            new Update().inc("reservasActivas", 1), FindAndModifyOptions.options().returnNew(true), Habitacion.class));
    }

    public boolean hayCruce(DatosReserva datos) {
        return hayCruce(datos.habitacionId(), datos.fechaEntrada(), datos.fechaSalida());
    }

    public boolean hayCruce(String habitacionId, String fechaEntrada, String fechaSalida) {
        return mongo.exists(Query.query(Criteria.where("datos.habitacionId").is(habitacionId)
            .and("estado").is(EstadoReserva.CONFIRMADA)
            .and("datos.fechaEntrada").lt(fechaSalida)
            .and("datos.fechaSalida").gt(fechaEntrada)), Reserva.class);
    }

    public Reserva cerrar(Reserva reserva, EstadoReserva estado) {
        var resultado = mongo.updateFirst(Query.query(Criteria.where("_id").is(reserva.datos().habitacionId())
            .and("reservasActivas").gt(0)), new Update().inc("reservasActivas", -1), Habitacion.class);
        if (resultado.getMatchedCount() != 1) {
            throw new DataIntegrityViolationException("Contador de reservas inconsistente.");
        }
        var actualizada = mongo.findAndModify(Query.query(Criteria.where("_id").is(reserva.id())
            .and("estado").is(EstadoReserva.CONFIRMADA)), new Update().set("estado", estado),
            FindAndModifyOptions.options().returnNew(true), Reserva.class);
        if (actualizada == null) { throw new DataIntegrityViolationException("Cambio de estado concurrente."); }
        return actualizada;
    }

    public List<Reserva> listar(EstadoReserva estado, String habitacionId, int pagina, int tamano) {
        return listar(estado, habitacionId, pagina, tamano, null);
    }

    public List<Reserva> listar(EstadoReserva estado, String habitacionId, int pagina, int tamano, String fecha) {
        return listar(estado, habitacionId, pagina, tamano, fecha, null);
    }

    public List<Reserva> listar(EstadoReserva estado, String habitacionId, int pagina, int tamano, String fecha, String buscar) {
        return mongo.find(filtro(estado, habitacionId, fecha, buscar).with(Sort.by("datos.fechaEntrada", "id"))
            .skip((long) pagina * tamano).limit(tamano), Reserva.class);
    }

    public long contar(EstadoReserva estado, String habitacionId) {
        return contar(estado, habitacionId, null);
    }

    public long contar(EstadoReserva estado, String habitacionId, String fecha) {
        return contar(estado, habitacionId, fecha, null);
    }

    public long contar(EstadoReserva estado, String habitacionId, String fecha, String buscar) {
        return mongo.count(filtro(estado, habitacionId, fecha, buscar), Reserva.class);
    }

    private Query filtro(EstadoReserva estado, String habitacionId, String fecha, String buscar) {
        Query query = new Query();
        if (buscar != null && !buscar.isEmpty()) {
            query.addCriteria(Criteria.where("datos.huesped").regex(Pattern.quote(buscar), "i"));
        }
        if (estado != null) { query.addCriteria(Criteria.where("estado").is(estado)); }
        if (habitacionId != null) { query.addCriteria(Criteria.where("datos.habitacionId").is(habitacionId)); }
        if (fecha != null) {
            query.addCriteria(Criteria.where("datos.fechaEntrada").lte(fecha));
            query.addCriteria(Criteria.where("datos.fechaSalida").gt(fecha));
        }
        return query;
    }
}
