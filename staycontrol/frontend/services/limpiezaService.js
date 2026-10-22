export class LimpiezaService {
    constructor(tareas = []) {
        this.tareas = new Map(tareas.map(tarea => [tarea.numero, tarea]));
    }

    alternarCompletada(numero) {
        const tarea = this.tareas.get(String(numero));
        if (!tarea) {
            throw new Error("No se encontró la tarea de limpieza.");
        }

        tarea.alternarCompletada();
        return tarea;
    }
}
