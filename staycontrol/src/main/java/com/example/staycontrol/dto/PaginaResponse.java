package com.example.staycontrol.dto;

import java.util.List;

public record PaginaResponse<T>(List<T> items, int pagina, int tamano, long total) {
    public PaginaResponse {
        items = List.copyOf(items);
    }
}
