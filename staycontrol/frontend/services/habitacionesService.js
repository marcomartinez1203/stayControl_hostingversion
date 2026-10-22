export class HabitacionesService {
    constructor(habitaciones = []) {
        this.habitaciones = [...habitaciones];
    }

    listar(filtro = "todas") {
        return this.habitaciones.filter(habitacion =>
            filtro === "todas" || habitacion.estado === filtro
        );
    }
}
