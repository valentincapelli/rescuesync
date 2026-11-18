import { useMemo, useState } from 'react';
import './AltaEmergenciaPage.css';
import './LotesPage.css';
import './IndicadoresPage.css';
import { BloqueIndicador } from '../components/indicadores/BloqueIndicador';
import { ExportarReporte } from '../components/indicadores/ExportarReporte';
import { FiltrosIndicadores } from '../components/indicadores/FiltrosIndicadores';
import { formatearFecha, mesesEntre, rangoEfectivo } from '../components/indicadores/formato';
import { fuenteIndicadores, USA_MOCKS } from '../components/indicadores/fuenteDatos';
import { GraficoConsorcios } from '../components/indicadores/GraficoConsorcios';
import { GraficoProyectosEstado } from '../components/indicadores/GraficoProyectosEstado';
import { GraficoSuministros } from '../components/indicadores/GraficoSuministros';
import { GraficoTiemposEtapas } from '../components/indicadores/GraficoTiemposEtapas';
import { KpisResumen } from '../components/indicadores/KpisResumen';
import { TablaProyectos } from '../components/indicadores/TablaProyectos';
import { useBloque } from '../components/indicadores/useBloque';
import { ESTADO_EMERGENCIA_LABELS, type EstadoEmergencia } from '../types/emergencia';
import type { FiltroIndicadores } from '../types/indicadores';

/**
 * Dashboard de indicadores para la Dirección (rol DIRECTIVO, E4). Cada bloque pide su endpoint
 * en paralelo y maneja su propia carga y error: si uno falla (por ejemplo Bonita), el resto se
 * sigue viendo. Contrato: docs/e4-indicadores-contrato.md.
 */
export function IndicadoresPage() {
  const [filtro, setFiltro] = useState<FiltroIndicadores>({});
  const [estadoTabla, setEstadoTabla] = useState<EstadoEmergencia | null>(null);

  const resumen = useBloque(fuenteIndicadores.resumen, filtro, 'No se pudo cargar el resumen.');
  const suministros = useBloque(fuenteIndicadores.suministros, filtro, 'No se pudieron cargar los suministros.');
  const consorcios = useBloque(fuenteIndicadores.consorcios, filtro, 'No se pudieron cargar los consorcios.');
  const proyectos = useBloque(fuenteIndicadores.proyectos, filtro, 'No se pudieron cargar los proyectos.');
  const tiempos = useBloque(fuenteIndicadores.tiemposEtapas, filtro, 'No se pudieron cargar los tiempos por etapa.');

  // Rango efectivo: el que informa el backend en /resumen; si no llegó, el mismo default calculado acá.
  const rango =
    resumen.estado.tipo === 'ok'
      ? { desde: resumen.estado.datos.filtro.desde, hasta: resumen.estado.datos.filtro.hasta }
      : rangoEfectivo(filtro);
  const meses = useMemo(() => mesesEntre(rango.desde, rango.hasta), [rango.desde, rango.hasta]);

  function aplicarFiltro(nuevo: FiltroIndicadores) {
    setFiltro(nuevo);
    setEstadoTabla(null);
  }

  return (
    <section className="indicadores-page">
      <div className="ind-encabezado">
        <div>
          <h2>Indicadores de gestión</h2>
          <p className="muted">
            Período del {formatearFecha(`${rango.desde}T12:00:00Z`)} al {formatearFecha(`${rango.hasta}T12:00:00Z`)}.
            {USA_MOCKS && <span className="badge ind-badge-mock">Datos simulados</span>}
          </p>
        </div>
        <ExportarReporte filtro={filtro} desde={rango.desde} hasta={rango.hasta} />
      </div>

      <FiltrosIndicadores aplicado={filtro} onAplicar={aplicarFiltro} />

      <KpisResumen estado={resumen.estado} onReintentar={resumen.reintentar} />

      <div className="ind-grid">
        <BloqueIndicador
          titulo="Suministros movilizados"
          descripcion="Cantidad comprometida por mes y tipo de recurso, según el mes en que la oferta pasó a Comprometida."
          estado={suministros.estado}
          onReintentar={suministros.reintentar}
          estaVacio={(d) => d.series.length === 0}
          mensajeVacio="No hubo suministros movilizados en el período."
          className="ind-ancho-completo"
        >
          {(d) => <GraficoSuministros datos={d} meses={meses} />}
        </BloqueIndicador>

        <BloqueIndicador
          titulo="Proyectos por estado"
          descripcion="Emergencias registradas en el período, según su estado actual."
          estado={proyectos.estado}
          onReintentar={proyectos.reintentar}
          estaVacio={(d) => d.total === 0}
          mensajeVacio="No hay emergencias registradas en el período."
        >
          {(d) => <GraficoProyectosEstado datos={d} seleccionado={estadoTabla} onSeleccionar={setEstadoTabla} />}
        </BloqueIndicador>

        <BloqueIndicador
          titulo="Resolución mediante consorcios"
          descripcion="Lotes cubiertos en el período: resueltos por 2 o más ONGs (consorcio) o por una sola."
          estado={consorcios.estado}
          onReintentar={consorcios.reintentar}
          estaVacio={(d) => d.lotesResueltos === 0}
          mensajeVacio="No hubo lotes resueltos en el período."
        >
          {(d) => <GraficoConsorcios datos={d} />}
        </BloqueIndicador>

        <BloqueIndicador
          titulo="Tiempo promedio por etapa"
          descripcion="Según las tareas completadas en Bonita. Pasá el mouse por una barra para ver mediana, mínimo, máximo y muestras."
          estado={tiempos.estado}
          onReintentar={tiempos.reintentar}
          estaVacio={(d) => d.bonita.disponible && d.casosAnalizados === 0}
          mensajeVacio="No hay casos de Bonita para los filtros elegidos."
          className="ind-ancho-completo"
        >
          {(d) => <GraficoTiemposEtapas datos={d} />}
        </BloqueIndicador>

        <BloqueIndicador
          titulo="Proyectos"
          descripcion={
            estadoTabla
              ? `Filtrado por estado: ${ESTADO_EMERGENCIA_LABELS[estadoTabla]}.`
              : 'Hacé clic en el encabezado de una columna para ordenar.'
          }
          estado={proyectos.estado}
          onReintentar={proyectos.reintentar}
          estaVacio={(d) => d.proyectos.length === 0}
          mensajeVacio="No hay proyectos en el período."
          className="ind-ancho-completo"
        >
          {(d) => (
            <TablaProyectos proyectos={d.proyectos} filtroEstado={estadoTabla} onQuitarFiltro={() => setEstadoTabla(null)} />
          )}
        </BloqueIndicador>
      </div>
    </section>
  );
}
