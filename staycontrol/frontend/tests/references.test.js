import assert from "node:assert/strict";
import { readFile, readdir } from "node:fs/promises";
import { test } from "node:test";

const frontend = new URL("../", import.meta.url);
const views = ["login", "dashboard", "habitaciones", "reservas", "finanzas", "limpieza"];

test("las seis vistas cargan sus estilos y controladores desde las nuevas rutas", async () => {
    for (const view of views) {
        const html = await readFile(new URL(`views/${view}.html`, frontend), "utf8");
        assert.ok(html.includes(`href="../assets/css/${view}.css"`), view);
        assert.ok(html.includes(`type="module" src="../controllers/${view}Controller.js"`), view);
        assert.ok((await readFile(new URL(`assets/css/${view}.css`, frontend))).length > 0);
        assert.ok((await readFile(new URL(`controllers/${view}Controller.js`, frontend))).length > 0);
    }
});

test("los imports de controladores, modelos y servicios apuntan a archivos existentes", async () => {
    for (const folder of ["controllers", "models", "services"]) {
        for (const file of await readdir(new URL(`${folder}/`, frontend))) {
            if (!file.endsWith(".js")) continue;
            const moduleUrl = new URL(`${folder}/${file}`, frontend);
            const code = await readFile(moduleUrl, "utf8");
            for (const [, path] of code.matchAll(/from\s+["']([^"']+)["']/g)) {
                assert.ok(path.startsWith("."), `Import inesperado en ${file}: ${path}`);
                assert.ok((await readFile(new URL(path, moduleUrl))).length > 0);
            }
        }
    }
});
