import assert from "node:assert/strict";
import { test } from "node:test";
import { Habitacion } from "../models/Habitacion.js";
import { TareaLimpieza } from "../models/TareaLimpieza.js";
import { HabitacionesService } from "../services/habitacionesService.js";
import { LimpiezaService } from "../services/limpiezaService.js";
import { requestJson } from "../services/apiService.js";

test("los filtros conservan todas las habitaciones y permiten volver a Todas", () => {
    const habitaciones = [
        new Habitacion({ numero: 101, estado: "disponibles" }),
        new Habitacion({ numero: 102, estado: "ocupadas" }),
        new Habitacion({ numero: 103, estado: "limpieza" }),
        new Habitacion({ numero: 104, estado: "mantenimiento" })
    ];
    const service = new HabitacionesService(habitaciones);

    assert.deepEqual(service.listar("disponibles").map(item => item.numero), ["101"]);
    assert.deepEqual(service.listar("ocupadas").map(item => item.numero), ["102"]);
    assert.deepEqual(service.listar("limpieza").map(item => item.numero), ["103"]);
    assert.deepEqual(service.listar("todas"), habitaciones);
    assert.equal(service.listar().length, 4);
    assert.deepEqual(service.listar("inexistente"), []);
});

test("completar y desmarcar una tarea no cambia las otras tareas", () => {
    const tareas = [
        new TareaLimpieza({ numero: 103 }),
        new TareaLimpieza({ numero: 201, estado: "process" }),
        new TareaLimpieza({ numero: 105, estado: "completed" })
    ];
    const service = new LimpiezaService(tareas);

    assert.equal(service.alternarCompletada("103").estado, "completed");
    assert.equal(tareas[1].estado, "process");
    assert.equal(tareas[2].estado, "completed");
    assert.equal(service.alternarCompletada(103).estado, "pending");
    assert.equal(service.alternarCompletada("201").estado, "completed");
    assert.equal(service.alternarCompletada("201").estado, "pending");
    assert.equal(service.alternarCompletada("105").estado, "pending");
    assert.throws(() => service.alternarCompletada("999"), /No se encontró/);
});

test("el cliente API envía JSON y conserva las opciones de la petición", async context => {
    const fetchMock = context.mock.method(globalThis, "fetch", async (url, options) => {
        if (url === "/api/v1/auth/csrf") {
            return Response.json({ headerName: "X-CSRF-TOKEN", token: "prueba-csrf" });
        }
        assert.equal(url, "/api/ejemplo");
        assert.equal(options.method, "POST");
        assert.equal(options.headers.get("Accept"), "application/json");
        assert.equal(options.headers.get("Content-Type"), "application/json");
        assert.equal(options.headers.get("X-Test"), "mvc");
        assert.equal(options.headers.get("X-CSRF-TOKEN"), "prueba-csrf");
        assert.equal(options.credentials, "same-origin");
        assert.deepEqual(JSON.parse(options.body), { numero: 103 });
        return new Response(JSON.stringify({ id: 1 }), {
            status: 201,
            headers: { "Content-Type": "application/json" }
        });
    });

    assert.deepEqual(await requestJson("/api/ejemplo", {
        method: "POST",
        headers: { "X-Test": "mvc" },
        body: { numero: 103 }
    }), { id: 1 });
    assert.equal(fetchMock.mock.callCount(), 2);
});

test("el cliente API acepta respuestas vacías y propaga los errores HTTP", async context => {
    const fetchMock = context.mock.method(globalThis, "fetch", async url =>
        url === "/api/v1/auth/csrf"
            ? Response.json({ headerName: "X-CSRF-TOKEN", token: "prueba-csrf" })
            : new Response(null, { status: 204 })
    );
    assert.equal(await requestJson("/api/ejemplo", { method: "DELETE" }), null);
    fetchMock.mock.mockImplementation(async () => new Response(null, { status: 404 }));
    await assert.rejects(requestJson("/api/ejemplo"), /HTTP 404/);
});
