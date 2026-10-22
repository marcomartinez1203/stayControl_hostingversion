package com.example.staycontrol.repository;

import com.example.staycontrol.dto.ResumenFinanzasResponse;
import com.example.staycontrol.model.*;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public class FinanzasRepository {
    private final MongoTemplate mongo;
    private final java.util.Set<String> indicesListos = new java.util.HashSet<>();
    public FinanzasRepository(MongoTemplate mongo) { this.mongo = mongo; }

    public synchronized void prepararIndices() {
        String coleccion = mongo.getCollectionName(MovimientoFinanciero.class);
        if (indicesListos.contains(coleccion)) { return; }
        mongo.indexOps(MovimientoFinanciero.class).createIndex(new Index().on("datos.moneda", Sort.Direction.ASC)
            .on("estado", Sort.Direction.ASC).on("datos.fecha", Sort.Direction.ASC).named("ix_finanzas_periodo"));
        indicesListos.add(coleccion);
    }
    public Optional<MovimientoFinanciero> buscar(String id) {
        return Optional.ofNullable(mongo.findById(id, MovimientoFinanciero.class));
    }
    public MovimientoFinanciero insertar(MovimientoFinanciero movimiento) { return mongo.insert(movimiento); }

    public Optional<MovimientoFinanciero> anular(String id, MovimientoFinanciero.Anulacion anulacion) {
        return Optional.ofNullable(mongo.findAndModify(Query.query(Criteria.where("_id").is(id)
            .and("estado").is(EstadoMovimiento.REGISTRADO)),
            new Update().set("estado", EstadoMovimiento.ANULADO).set("anulacion", anulacion),
            FindAndModifyOptions.options().returnNew(true), MovimientoFinanciero.class));
    }

    public List<MovimientoFinanciero> listar(TipoMovimiento tipo, EstadoMovimiento estado, String moneda,
        String desde, String hasta, int pagina, int tamano) {
        return mongo.find(Query.query(filtro(tipo, estado, moneda, desde, hasta))
            .with(Sort.by(Sort.Direction.DESC, "datos.fecha", "id")).skip((long) pagina * tamano).limit(tamano),
            MovimientoFinanciero.class);
    }
    public long contar(TipoMovimiento tipo, EstadoMovimiento estado, String moneda, String desde, String hasta) {
        return mongo.count(Query.query(filtro(tipo, estado, moneda, desde, hasta)), MovimientoFinanciero.class);
    }

    public ResumenFinanzasResponse resumir(String moneda, String desde, String hasta) {
        var aggregation = Aggregation.newAggregation(
            Aggregation.match(filtro(null, EstadoMovimiento.REGISTRADO, moneda, desde, hasta)),
            Aggregation.group("datos.tipo").sum("datos.importe").as("total").count().as("cantidad"));
        BigDecimal ingresos = BigDecimal.ZERO;
        BigDecimal egresos = BigDecimal.ZERO;
        long cantidad = 0;
        for (Document fila : mongo.aggregate(aggregation, MovimientoFinanciero.class, Document.class).getMappedResults()) {
            Object suma = fila.get("total");
            BigDecimal total = suma instanceof Decimal128 decimal ? decimal.bigDecimalValue() : new BigDecimal(suma.toString());
            if (TipoMovimiento.INGRESO.name().equals(fila.getString("_id"))) { ingresos = total; }
            else if (TipoMovimiento.EGRESO.name().equals(fila.getString("_id"))) { egresos = total; }
            cantidad += ((Number) fila.get("cantidad")).longValue();
        }
        return new ResumenFinanzasResponse(moneda, desde, hasta, ingresos, egresos, ingresos.subtract(egresos), cantidad);
    }

    private Criteria filtro(TipoMovimiento tipo, EstadoMovimiento estado, String moneda, String desde, String hasta) {
        Criteria criteria = new Criteria();
        if (tipo != null) { criteria.and("datos.tipo").is(tipo); }
        if (estado != null) { criteria.and("estado").is(estado); }
        if (moneda != null) { criteria.and("datos.moneda").is(moneda); }
        if (desde != null || hasta != null) {
            Criteria fecha = criteria.and("datos.fecha");
            if (desde != null) { fecha.gte(desde); }
            if (hasta != null) { fecha.lte(hasta); }
        }
        return criteria;
    }
}
