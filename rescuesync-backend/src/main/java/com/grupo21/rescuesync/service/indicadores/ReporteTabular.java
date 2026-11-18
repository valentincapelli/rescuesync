package com.grupo21.rescuesync.service.indicadores;

import com.grupo21.rescuesync.dto.indicadores.ConsorcioPorTipoRecursoResponse;
import com.grupo21.rescuesync.dto.indicadores.FiltroIndicadores;
import com.grupo21.rescuesync.dto.indicadores.ReporteIndicadoresResponse;
import com.grupo21.rescuesync.dto.indicadores.ResolucionConsorciosResponse;
import com.grupo21.rescuesync.dto.indicadores.ResumenIndicadoresResponse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * El reporte de indicadores como tablas: una {@link Seccion} por bloque. Es la fuente común de las
 * exportaciones CSV y XLSX, para que los dos formatos traigan exactamente lo mismo.
 *
 * <p>Las celdas son {@code String}, {@code Number}, {@code Boolean}, {@code Instant}, {@code Enum} o
 * {@code null} (celda vacía). El formato lo decide cada escritor.
 */
public final class ReporteTabular {

    /** Una tabla del reporte. */
    public record Seccion(String titulo, List<String> encabezados, List<List<Object>> filas) {}

    private ReporteTabular() {
    }

    public static List<Seccion> desde(ReporteIndicadoresResponse reporte) {
        ResumenIndicadoresResponse resumen = reporte.resumen();
        List<Seccion> secciones = new ArrayList<>();
        secciones.add(filtros(resumen));
        secciones.add(resumen(resumen));

        secciones.add(new Seccion("Suministros por mes",
                List.of("Mes", "Tipo de recurso", "Unidad", "Cantidad", "Ofertas"),
                reporte.suministros().series().stream()
                        .map(s -> fila(s.mes(), s.tipoRecurso(), s.unidadMedida(), s.cantidad(), s.ofertas()))
                        .toList()));

        secciones.add(new Seccion("Suministros totales",
                List.of("Tipo de recurso", "Unidad", "Cantidad", "Ofertas"),
                reporte.suministros().totales().stream()
                        .map(t -> fila(t.tipoRecurso(), t.unidadMedida(), t.cantidad(), t.ofertas()))
                        .toList()));

        secciones.add(consorcios(reporte.consorcios()));

        secciones.add(new Seccion("Consorcios por tipo",
                List.of("Tipo de recurso", "Lotes resueltos", "Lotes por consorcio", "% por consorcio"),
                reporte.consorcios().porTipoRecurso().stream()
                        .map(ReporteTabular::filaConsorcio)
                        .toList()));

        secciones.add(new Seccion("Proyectos por estado",
                List.of("Estado", "Cantidad"),
                reporte.proyectos().porEstado().stream()
                        .map(c -> fila(c.estado(), c.cantidad()))
                        .toList()));

        secciones.add(new Seccion("Proyectos",
                List.of("Id", "Tipo de desastre", "Gravedad", "Municipio", "Zona afectada", "Estado",
                        "Fecha de registro", "Fecha de cierre", "Lotes", "% cobertura", "Caso Bonita"),
                reporte.proyectos().proyectos().stream()
                        .map(p -> fila(p.id(), p.tipoDesastre(), p.nivelGravedad(), p.municipio(),
                                p.zonaAfectada(), p.estado(), p.fechaRegistro(), p.fechaCierre(), p.lotes(),
                                p.porcentajeCobertura(), p.bonitaCaseId()))
                        .toList()));

        secciones.add(new Seccion("Tiempos por etapa",
                List.of("Etapa", "Nombre", "Tareas de Bonita", "Muestras", "Promedio (h)", "Mediana (h)",
                        "Mínimo (h)", "Máximo (h)"),
                reporte.tiemposEtapas().etapas().stream()
                        .map(t -> fila(t.etapa(), t.nombre(), String.join(", ", t.tareas()), t.muestras(),
                                t.promedioHoras(), t.medianaHoras(), t.minimoHoras(), t.maximoHoras()))
                        .toList()));

        return secciones;
    }

    private static Seccion filtros(ResumenIndicadoresResponse resumen) {
        FiltroIndicadores f = resumen.filtro();
        return new Seccion("Filtros", List.of("Parámetro", "Valor"), List.of(
                fila("Generado", resumen.generadoEn()),
                fila("Desde", f.desde().toString()),
                fila("Hasta", f.hasta().toString()),
                fila("Municipio (id)", f.municipioId()),
                fila("Tipo de desastre", f.tipoDesastre()),
                fila("Tipo de recurso", f.tipoRecurso()),
                fila("Datos de Bonita disponibles", resumen.bonita().disponible()),
                fila("Motivo (Bonita)", resumen.bonita().mensaje())));
    }

    private static Seccion resumen(ResumenIndicadoresResponse r) {
        return new Seccion("Resumen", List.of("Indicador", "Valor"), List.of(
                fila("Emergencias totales", r.emergenciasTotales()),
                fila("Emergencias activas", r.emergenciasActivas()),
                fila("Proyectos finalizados", r.proyectosFinalizados()),
                fila("% finalizados", r.porcentajeFinalizados()),
                fila("Lotes resueltos", r.lotesResueltos()),
                fila("% resolución por consorcio", r.porcentajeResolucionConsorcio()),
                fila("Tiempo promedio de resolución (h)", r.tiempoPromedioResolucionHoras())));
    }

    private static Seccion consorcios(ResolucionConsorciosResponse c) {
        return new Seccion("Consorcios", List.of("Indicador", "Valor"), List.of(
                fila("Lotes resueltos", c.lotesResueltos()),
                fila("Lotes por consorcio", c.lotesPorConsorcio()),
                fila("Lotes por una sola ONG", c.lotesPorOngUnica()),
                fila("% por consorcio", c.porcentajeConsorcio()),
                fila("ONGs promedio por consorcio", c.promedioOngsPorConsorcio())));
    }

    private static List<Object> filaConsorcio(ConsorcioPorTipoRecursoResponse c) {
        return fila(c.tipoRecurso(), c.lotesResueltos(), c.lotesPorConsorcio(), c.porcentajeConsorcio());
    }

    /** Lista que admite nulls (List.of no los admite). */
    private static List<Object> fila(Object... celdas) {
        return Arrays.asList(celdas);
    }
}
