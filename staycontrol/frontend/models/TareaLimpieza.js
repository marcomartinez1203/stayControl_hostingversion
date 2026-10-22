export class TareaLimpieza {
    constructor({ numero, estado = "pending" }) {
        this.numero = String(numero);
        this.estado = estado;
    }

    alternarCompletada() {
        this.estado = this.estado === "completed" ? "pending" : "completed";
    }
}
