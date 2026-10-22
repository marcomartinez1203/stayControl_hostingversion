package com.example.staycontrol.dto;

import java.math.BigDecimal;

public record ResumenFinanzasResponse(String moneda, String desde, String hasta,
    BigDecimal ingresos, BigDecimal egresos, BigDecimal balance, long movimientos) { }
