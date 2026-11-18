import type { ResumenIndicadores } from '../../types/indicadores';
import { AvisoBonita } from './AvisoBonita';
import { formatearDuracion, formatearNumero, formatearPorcentaje } from './formato';
import type { EstadoBloque } from './useBloque';

interface Props {
  estado: EstadoBloque<ResumenIndicadores>;
  onReintentar: () => void;
}

interface Kpi {
  titulo: string;
  valor: string;
  detalle: string;
  ayuda: string;
  sinDatos?: boolean;
}

function armarKpis(r: ResumenIndicadores): Kpi[] {
  const bonitaCaido = !r.bonita.disponible;
  return [
    {
      titulo: 'Emergencias',
      valor: formatearNumero(r.emergenciasTotales),
      detalle: 'registradas en el período',
      ayuda: 'Emergencias creadas dentro del período, con los filtros de municipio y tipo de desastre.',
    },
    {
      titulo: 'Activas',
      valor: formatearNumero(r.emergenciasActivas),
      detalle: 'todavía no cerradas',
      ayuda: 'Emergencias del período cuyo estado actual no es Cerrada.',
    },
    {
      titulo: 'Proyectos finalizados',
      valor: formatearPorcentaje(r.porcentajeFinalizados),
      detalle: `${formatearNumero(r.proyectosFinalizados)} de ${formatearNumero(r.emergenciasTotales)} cerrados`,
      ayuda: 'Porcentaje de emergencias del período en estado Cerrada.',
      sinDatos: r.porcentajeFinalizados === null,
    },
    {
      titulo: 'Resueltos por consorcio',
      valor: formatearPorcentaje(r.porcentajeResolucionConsorcio),
      detalle: `sobre ${formatearNumero(r.lotesResueltos)} lotes resueltos`,
      ayuda:
        'Lotes cubiertos en el período cuyas ofertas adjudicadas son de 2 o más ONGs distintas, sobre el total de lotes resueltos.',
      sinDatos: r.porcentajeResolucionConsorcio === null,
    },
    {
      titulo: 'Tiempo promedio de resolución',
      valor: formatearDuracion(r.tiempoPromedioResolucionHoras),
      detalle: bonitaCaido ? 'Bonita no disponible' : 'casos finalizados en Bonita',
      ayuda: 'Promedio de duración (inicio a fin) de los casos terminados en Bonita.',
      sinDatos: r.tiempoPromedioResolucionHoras === null,
    },
  ];
}

/** Fila de KPIs a partir de /indicadores/resumen. */
export function KpisResumen({ estado, onReintentar }: Props) {
  if (estado.tipo === 'error') {
    return (
      <div className="ind-estado ind-estado-error ind-kpis-error" role="alert">
        <p>No se pudo cargar el resumen: {estado.mensaje}</p>
        <button type="button" className="secondary" onClick={onReintentar}>
          Reintentar
        </button>
      </div>
    );
  }

  const datos = estado.datos;
  if (datos === null) {
    return (
      <div className="ind-kpis" aria-busy="true">
        {Array.from({ length: 5 }, (_, i) => (
          <div key={i} className="ind-kpi ind-kpi-cargando">
            <span className="ind-kpi-titulo">Cargando…</span>
            <span className="ind-kpi-valor">—</span>
          </div>
        ))}
      </div>
    );
  }

  return (
    <>
      <AvisoBonita bonita={datos.bonita} />
      <div className={estado.tipo === 'cargando' ? 'ind-kpis actualizando' : 'ind-kpis'} aria-busy={estado.tipo === 'cargando'}>
        {armarKpis(datos).map((kpi) => (
          <div key={kpi.titulo} className="ind-kpi" title={kpi.ayuda}>
            <span className="ind-kpi-titulo">{kpi.titulo}</span>
            <span className={kpi.sinDatos ? 'ind-kpi-valor sin-datos' : 'ind-kpi-valor'}>{kpi.valor}</span>
            <span className="ind-kpi-detalle">{kpi.detalle}</span>
          </div>
        ))}
      </div>
    </>
  );
}
