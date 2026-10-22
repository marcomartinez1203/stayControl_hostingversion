package com.example.staycontrol.model;

import java.math.BigDecimal;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

public record DatosMovimiento(TipoMovimiento tipo, String concepto,
    @Field(targetType = FieldType.DECIMAL128) BigDecimal importe,
    String moneda, String fecha, MetodoPago metodoPago) {
    public boolean coincideCon(DatosMovimiento otro) {
        return tipo == otro.tipo && concepto.equals(otro.concepto) && importe.compareTo(otro.importe) == 0
            && moneda.equals(otro.moneda) && fecha.equals(otro.fecha) && metodoPago == otro.metodoPago;
    }
}
