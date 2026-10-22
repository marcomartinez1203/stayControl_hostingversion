/* Incrementar VERSION al publicar cambios en los recursos offline. */
const VERSION = "staycontrol-pwa-vue-v3";
const OFFLINE = "/offline.html";
const APP_SHELL = "/index.html";
// Lista cerrada: nada de sesiones, perfiles, HTML de negocio ni respuestas API.
const PUBLICOS = [OFFLINE, APP_SHELL, "/assets/css/pwa.css", "/controllers/pwaController.js",
    "/assets/fonts/DMSans-Variable.ttf", "/assets/icons/pwa-192.png", "/assets/icons/pwa-512.png"];

self.addEventListener("install", event => {
    event.waitUntil(caches.open(VERSION).then(cache => cache.addAll(
        PUBLICOS.map(url => new Request(url, { cache: "reload", credentials: "omit" }))
    )));
    // Sin skipWaiting: no sustituir el worker de una pestaña con formularios abiertos.
});

self.addEventListener("activate", event => {
    event.waitUntil((async () => {
        const nombres = await caches.keys();
        await Promise.all(nombres.filter(nombre => nombre.startsWith("staycontrol-pwa-") && nombre !== VERSION)
            .map(nombre => caches.delete(nombre)));
        await self.clients.claim();
    })());
});

self.addEventListener("fetch", event => {
    const request = event.request;
    const url = new URL(request.url);
    // Las escrituras y todas las APIs usan exclusivamente la red. Nunca se encolan.
    if (request.method !== "GET" || url.origin !== self.location.origin || url.pathname.startsWith("/api/")) return;

    // Navegaciones: Network-first → App Shell cacheado → offline.html → texto plano.
    if (request.mode === "navigate") {
        event.respondWith((async () => {
            try { return await fetch(request); }
            catch {
                const cache = await caches.open(VERSION);
                return await cache.match(APP_SHELL)
                    || await cache.match(OFFLINE)
                    || new Response("Sin conexión. Vuelve a intentarlo cuando el servidor esté disponible.", {
                        status: 503, headers: { "Content-Type": "text/plain; charset=utf-8" }
                    });
            }
        })());
        return;
    }

    // Assets de Vue (hashes en nombre → inmutables): cache-first con cacheado en runtime.
    if (url.pathname.startsWith("/vue-assets/")) {
        event.respondWith((async () => {
            const cache = await caches.open(VERSION);
            const cached = await cache.match(request);
            if (cached) return cached;
            const response = await fetch(request);
            if (response.ok) cache.put(request, response.clone());
            return response;
        })());
        return;
    }

    // Recursos públicos pre-cacheados: cache-first con fallback a red.
    if (PUBLICOS.includes(url.pathname) && !url.search) {
        event.respondWith((async () => {
            const cache = await caches.open(VERSION);
            return await cache.match(url.pathname) || fetch(request);
        })());
    }
});
