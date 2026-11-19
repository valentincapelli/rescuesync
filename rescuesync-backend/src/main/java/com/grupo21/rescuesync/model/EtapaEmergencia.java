package com.grupo21.rescuesync.model;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Etapas de la gestión de una emergencia, agrupando las tareas del proceso {@code RescueSync}
 * de Bonita (rescue-sync.bos, versión 1.1). Se usa para el indicador "tiempo promedio por etapa".
 *
 * <p>Los nombres de tarea deben coincidir exactamente con los del diagrama. Si se renombra una
 * tarea en Bonita Studio, hay que actualizarla acá.
 */
public enum EtapaEmergencia {

    REGISTRO("Registro y desglose",
            List.of("Registrar emergencia", "Revisar y desglosar emergencia")),
    CONVOCATORIA("Convocatoria",
            List.of("Publicar convocatoria", "Esperar /cargar / editar ofertas")),
    EVALUACION("Evaluación y selección",
            List.of("Evaluar cobertura de lotes", "Evaluar ofertas", "Resolver cobertura incompleta",
                    "Seleccionar oferta")),
    COMPROMISO("Compromiso de recursos",
            List.of("Comprometer recursos", "Notificar a ONG", "Recibir notificación")),
    EJECUCION("Ejecución",
            List.of("Monitorear despliegue", "Finalizar actividad")),
    CIERRE("Cierre",
            List.of("Liberar recursos"));

    private final String nombre;
    private final List<String> tareas;

    EtapaEmergencia(String nombre, List<String> tareas) {
        this.nombre = nombre;
        this.tareas = tareas;
    }

    public String getNombre() {
        return nombre;
    }

    public List<String> getTareas() {
        return tareas;
    }

    /** Etapa a la que pertenece una tarea de Bonita, por su nombre exacto. */
    public static Optional<EtapaEmergencia> deTarea(String nombreTarea) {
        return Arrays.stream(values())
                .filter(etapa -> etapa.tareas.contains(nombreTarea))
                .findFirst();
    }
}
