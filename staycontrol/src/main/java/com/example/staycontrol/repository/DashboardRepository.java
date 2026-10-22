package com.example.staycontrol.repository;

import com.example.staycontrol.dto.DashboardResponse;
import com.example.staycontrol.model.*;
import org.bson.Document;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Solo consultas: no crea índices, registros ni resúmenes persistentes. */
@Repository
public class DashboardRepository {
    private final MongoTemplate mongo;
    public DashboardRepository(MongoTemplate mongo) { this.mongo = mongo; }

    public DashboardResponse.Habitaciones habitaciones() {
        Map<String, Long> estados = contarPorEstado(Habitacion.class, "datos.estado", Criteria.where("eliminada").is(false));
        long total = estados.values().stream().mapToLong(Long::longValue).sum();
        long ocupadas = estados.getOrDefault(EstadoHabitacion.OCUPADA.name(), 0L);
        BigDecimal porcentaje = total == 0 ? new BigDecimal("0.00")
            : BigDecimal.valueOf(ocupadas).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
        return new DashboardResponse.Habitaciones(total,
            estados.getOrDefault(EstadoHabitacion.DISPONIBLE.name(), 0L), ocupadas,
            estados.getOrDefault(EstadoHabitacion.LIMPIEZA.name(), 0L),
            estados.getOrDefault(EstadoHabitacion.MANTENIMIENTO.name(), 0L), porcentaje);
    }

    public DashboardResponse.ReservasDia reservasDelDia(String fecha) {
        long entradas = mongo.count(Query.query(confirmadas().and("datos.fechaEntrada").is(fecha)), Reserva.class);
        long salidas = mongo.count(Query.query(confirmadas().and("datos.fechaSalida").is(fecha)), Reserva.class);
        long estancias = mongo.count(Query.query(confirmadas().and("datos.fechaEntrada").lte(fecha)
            .and("datos.fechaSalida").gt(fecha)), Reserva.class);
        return new DashboardResponse.ReservasDia(entradas, salidas, estancias);
    }

    public DashboardResponse.Limpieza limpieza(String fecha) {
        Map<String, Long> estados = contarPorEstado(TareaLimpieza.class, "estado", tareasAbiertas(fecha));
        var distintas = mongo.aggregate(Aggregation.newAggregation(
            Aggregation.match(tareasAbiertas(fecha)), Aggregation.group("datos.habitacionId"),
            Aggregation.count().as("cantidad")), TareaLimpieza.class, Document.class).getUniqueMappedResult();
        long habitaciones = distintas == null ? 0 : ((Number) distintas.get("cantidad")).longValue();
        return new DashboardResponse.Limpieza(estados.getOrDefault(EstadoLimpieza.PENDIENTE.name(), 0L),
            estados.getOrDefault(EstadoLimpieza.EN_PROCESO.name(), 0L), habitaciones);
    }

    public List<DashboardResponse.ProximaReserva> proximasReservas(String fecha, int limite) {
        List<Reserva> reservas = mongo.find(Query.query(confirmadas().and("datos.fechaEntrada").gte(fecha))
            .with(Sort.by("datos.fechaEntrada", "id")).limit(limite), Reserva.class);
        if (reservas.isEmpty()) { return List.of(); }
        var ids = reservas.stream().map(reserva -> reserva.datos().habitacionId()).distinct().toList();
        // Una sola consulta acotada para los números, sin una consulta adicional por reserva.
        Map<String, String> numeros = mongo.find(Query.query(Criteria.where("_id").in(ids)), Habitacion.class)
            .stream().collect(Collectors.toMap(Habitacion::id, habitacion -> habitacion.datos().numero()));
        return reservas.stream().map(reserva -> {
            var datos = reserva.datos();
            return new DashboardResponse.ProximaReserva(reserva.id(), datos.habitacionId(),
                numeros.get(datos.habitacionId()), datos.huesped(), datos.personas(), datos.fechaEntrada(), datos.fechaSalida());
        }).toList();
    }

    private Map<String, Long> contarPorEstado(Class<?> tipo, String campo, Criteria filtro) {
        var resultados = mongo.aggregate(Aggregation.newAggregation(Aggregation.match(filtro),
            Aggregation.group(campo).count().as("cantidad")), tipo, Document.class).getMappedResults();
        Map<String, Long> estados = new HashMap<>();
        for (Document fila : resultados) {
            String estado = fila.getString("_id");
            if (estado == null) { throw new DataIntegrityViolationException("Registro sin estado en el resumen."); }
            estados.put(estado, ((Number) fila.get("cantidad")).longValue());
        }
        return estados;
    }
    private Criteria confirmadas() { return Criteria.where("estado").is(EstadoReserva.CONFIRMADA); }
    private Criteria tareasAbiertas(String fecha) {
        return Criteria.where("estado").in(EstadoLimpieza.PENDIENTE, EstadoLimpieza.EN_PROCESO)
            .and("datos.fechaProgramada").lte(fecha);
    }
}
