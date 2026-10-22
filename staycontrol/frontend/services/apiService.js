/**
 * Cliente JSON de la API: cookies de sesión y CSRF para escrituras del mismo origen.
 * No reintenta automáticamente ni genera claves de idempotencia por petición.
 */
export async function requestJson(url, { body, headers, ...options } = {}) {
    const requestHeaders = new Headers(headers);
    if (!requestHeaders.has("Accept")) {
        requestHeaders.set("Accept", "application/json");
    }
    if (body !== undefined && !requestHeaders.has("Content-Type")) {
        requestHeaders.set("Content-Type", "application/json");
    }

    const method = (options.method ?? "GET").toUpperCase();
    if (typeof url === "string" && url.startsWith("/api/") && ["POST", "PUT", "PATCH", "DELETE"].includes(method)) {
        // Leer un token fresco permite la rotación tras login/logout sin usar localStorage.
        const csrfResponse = await fetch("/api/v1/auth/csrf", {
            credentials: "same-origin", cache: "no-store", signal: options.signal,
            headers: { Accept: "application/json" }
        });
        if (!csrfResponse.ok) { throw await apiError(csrfResponse); }
        const csrf = await csrfResponse.json();
        requestHeaders.set(csrf.headerName, csrf.token);
    }

    const response = await fetch(url, {
        credentials: "same-origin",
        ...options,
        headers: requestHeaders,
        ...(body !== undefined ? { body: JSON.stringify(body) } : {})
    });

    if (!response.ok) {
        throw await apiError(response);
    }
    if (response.status === 204) {
        return null;
    }
    return response.json();
}

export class ApiError extends Error {
    constructor(status, codigo, mensaje) {
        super(`${mensaje} (HTTP ${status}).`);
        this.name = "ApiError";
        this.status = status;
        this.codigo = codigo;
    }
}

async function apiError(response) {
    let body;
    try { body = await response.json(); } catch { /* Puede ser una respuesta sin JSON. */ }
    return new ApiError(response.status, body?.codigo ?? "ERROR_HTTP",
        body?.mensaje ?? "La solicitud falló");
}
