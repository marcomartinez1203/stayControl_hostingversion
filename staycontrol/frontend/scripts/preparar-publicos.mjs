import { cp, mkdir, writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import path from "node:path";
const root = fileURLToPath(new URL("../", import.meta.url));
const publico = path.join(root, ".public");
await mkdir(path.join(publico, "controllers"), { recursive: true });
await mkdir(path.join(publico, "views"), { recursive: true });
await cp(path.join(root, "assets"), path.join(publico, "assets"), { recursive: true });
for (const file of ["manifest.json", "sw.js", "offline.html", "controllers/pwaController.js"]) {
    await cp(path.join(root, file), path.join(publico, file));
}
// Mantiene marcadores/enlaces antiguos de Spring sin servir el frontend anterior.
for (const pagina of ["login", "registro", "recuperar-contrasena", "dashboard", "habitaciones", "reservas", "limpieza", "finanzas"]) {
    const codigo = `const base='/${pagina}';let ruta=base;const h=location.hash.slice(1);const q=new URLSearchParams(location.search);const qi=h.indexOf('?');const hp=qi>=0?h.slice(0,qi):h;if(qi>=0)new URLSearchParams(h.slice(qi+1)).forEach((v,k)=>q.set(k,v));if(base==='/habitaciones'&&hp){if(hp.startsWith('/editar/'))ruta=base+hp;else if(hp==='/nueva')ruta=base+'/nuevo';}if(['/reservas','/limpieza'].includes(base)&&q.get('crear')==='1'){ruta=base+'/nuevo';q.delete('crear');}location.replace('/index.html#'+ruta+(q.size?'?'+q:''));`;
    await writeFile(path.join(publico, "views", `${pagina}.html`), `<!doctype html><html lang="es"><meta charset="utf-8"><title>StayControl</title><p>Abriendo StayControl…</p><script type="module">${codigo}</script><noscript>Activa JavaScript para usar StayControl.</noscript></html>`);
}
