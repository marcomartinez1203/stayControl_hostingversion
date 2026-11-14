// Precarga de la primera vista, solo en memoria y durante la apertura del documento.
// Observa las lecturas existentes: no duplica peticiones ni almacena respuestas.
const pending = new Set();
let collecting = true;

export function trackStartupRead(promise) {
    if (!collecting) return promise;
    pending.add(promise);
    const done = () => pending.delete(promise);
    promise.then(done, done);
    return promise;
}

export async function waitForStartupReads(maxWait = 15000) {
    const started = performance.now();
    // Un turno permite que los componentes encadenen sus lecturas y rendericen.
    do {
        await new Promise(resolve => setTimeout(resolve, 50));
    } while (pending.size && performance.now() - started < maxWait);
    const completed = pending.size === 0;
    collecting = false;
    pending.clear();
    return completed;
}
