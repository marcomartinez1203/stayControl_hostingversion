import assert from "node:assert/strict";
import { test } from "node:test";
import { iniciarSesion, consultarSesion, cerrarSesion } from "../services/authService.js";
import { requestJson, ApiError } from "../services/apiService.js";

test("login conserva la contraseña y usa CSRF y cookies sin guardarla", async context => {
    const llamadas = [];
    context.mock.method(globalThis, "fetch", async (url, options) => {
        llamadas.push({ url, options });
        if (url.endsWith("/csrf")) { return Response.json({ headerName: "X-CSRF-TOKEN", token: "token-1" }); }
        return Response.json({ email: "admin@example.test" });
    });
    assert.deepEqual(await iniciarSesion("admin@example.test", "  prueba-contraseña  "), { email: "admin@example.test" });
    assert.equal(llamadas.length, 2);
    assert.equal(llamadas[0].options.cache, "no-store");
    const login = llamadas[1];
    assert.equal(login.url, "/api/v1/auth/login");
    assert.equal(JSON.parse(login.options.body).password, "  prueba-contraseña  ");
    assert.equal(login.options.headers.get("X-CSRF-TOKEN"), "token-1");
    assert.equal(login.options.credentials, "same-origin");
});

test("consulta sesión sin escritura y logout con token nuevo", async context => {
    const urls = [];
    context.mock.method(globalThis, "fetch", async (url, options) => {
        urls.push(url);
        if (url.endsWith("/csrf")) { return Response.json({ headerName: "X-CSRF-TOKEN", token: "nuevo" }); }
        if (url.endsWith("/logout")) {
            assert.equal(options.method, "POST");
            assert.equal(options.headers.get("X-CSRF-TOKEN"), "nuevo");
            return new Response(null, { status: 204 });
        }
        return Response.json({ email: "admin@example.test" });
    });
    assert.equal((await consultarSesion()).email, "admin@example.test");
    assert.equal(await cerrarSesion(), null);
    assert.deepEqual(urls, ["/api/v1/auth/me", "/api/v1/auth/csrf", "/api/v1/auth/logout"]);
});

test("reintentos explícitos conservan Idempotency-Key y renuevan CSRF", async context => {
    let tokens = 0;
    const claves = [];
    context.mock.method(globalThis, "fetch", async (url, options) => {
        if (url.endsWith("/csrf")) { return Response.json({ headerName: "X-CSRF-TOKEN", token: `token-${++tokens}` }); }
        claves.push(options.headers.get("Idempotency-Key"));
        assert.equal(options.headers.get("X-CSRF-TOKEN"), `token-${tokens}`);
        return Response.json({ id: "mismo-id" });
    });
    const opciones = { method: "POST", headers: { "Idempotency-Key": "misma-operacion" }, body: { numero: "101" } };
    await requestJson("/api/v1/habitaciones", opciones);
    await requestJson("/api/v1/habitaciones", opciones);
    assert.deepEqual(claves, ["misma-operacion", "misma-operacion"]);
    assert.equal(tokens, 2);
});

test("error de credenciales conserva estado HTTP sin reintentar login", async context => {
    let intentos = 0;
    context.mock.method(globalThis, "fetch", async url => {
        if (url.endsWith("/csrf")) { return Response.json({ headerName: "X-CSRF-TOKEN", token: "token" }); }
        intentos++;
        return Response.json({ codigo: "CREDENCIALES_INVALIDAS", mensaje: "Email o contraseña incorrectos." }, { status: 401 });
    });
    await assert.rejects(iniciarSesion("admin@example.test", "incorrecta"), error =>
        error instanceof ApiError && error.status === 401 && error.codigo === "CREDENCIALES_INVALIDAS");
    assert.equal(intentos, 1);
});

test("si falla obtener CSRF no envía la contraseña", async context => {
    const mock = context.mock.method(globalThis, "fetch", async url => {
        assert.equal(url, "/api/v1/auth/csrf");
        return new Response(null, { status: 503 });
    });
    await assert.rejects(iniciarSesion("admin@example.test", "prueba"), /HTTP 503/);
    assert.equal(mock.mock.callCount(), 1);
});

test("no envía el token CSRF a un origen externo", async context => {
    const mock = context.mock.method(globalThis, "fetch", async (url, options) => {
        assert.equal(url, "https://example.test/api/externa");
        assert.equal(options.headers.has("X-CSRF-TOKEN"), false);
        return Response.json({ ok: true });
    });
    await requestJson("https://example.test/api/externa", { method: "POST", body: {} });
    assert.equal(mock.mock.callCount(), 1);
});
