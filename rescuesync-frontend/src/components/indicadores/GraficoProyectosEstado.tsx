import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  LabelList,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
  type TooltipContentProps,
} from 'recharts';
import { ESTADO_EMERGENCIA_LABELS, type EstadoEmergencia } from '../../types/emergencia';
import type { EstadoProyectos } from '../../types/indicadores';
import { COLOR_ATENUADO, COLOR_EJE, COLOR_GRILLA, COLOR_PRINCIPAL, formatearNumero, formatearPorcentaje } from './formato';

interface Props {
  datos: EstadoProyectos;
  seleccionado: EstadoEmergencia | null;
  onSeleccionar: (estado: EstadoEmergencia | null) => void;
}

interface Fila {
  estado: EstadoEmergencia;
  etiqueta: string;
  cantidad: number;
}

/** Barras horizontales de proyectos por estado. Clic en un estado filtra la tabla de proyectos. */
export function GraficoProyectosEstado({ datos, seleccionado, onSeleccionar }: Props) {
  const filas: Fila[] = datos.porEstado.map((p) => ({
    estado: p.estado,
    etiqueta: ESTADO_EMERGENCIA_LABELS[p.estado],
    cantidad: p.cantidad,
  }));

  function alternar(estado: EstadoEmergencia) {
    onSeleccionar(seleccionado === estado ? null : estado);
  }

  return (
    <div>
      <p className="ind-mini-resumen">
        <span>
          <strong>{formatearNumero(datos.total)}</strong> proyectos
        </span>
        <span>
          <strong>{formatearNumero(datos.enCurso)}</strong> en curso
        </span>
        <span>
          <strong>{formatearNumero(datos.finalizados)}</strong> finalizados ({formatearPorcentaje(datos.porcentajeFinalizados)})
        </span>
      </p>
      <div className="ind-grafico ind-grafico-clickeable">
        <ResponsiveContainer width="100%" height={300}>
          <BarChart data={filas} layout="vertical" margin={{ top: 0, right: 32, left: 0, bottom: 0 }}>
            <CartesianGrid horizontal={false} stroke={COLOR_GRILLA} />
            <XAxis
              type="number"
              allowDecimals={false}
              tick={{ fill: COLOR_EJE, fontSize: 12 }}
              tickLine={false}
              axisLine={{ stroke: COLOR_GRILLA }}
            />
            <YAxis
              type="category"
              dataKey="etiqueta"
              width={150}
              tick={{ fill: COLOR_EJE, fontSize: 12 }}
              tickLine={false}
              axisLine={false}
            />
            <Tooltip cursor={{ fill: 'var(--ind-cursor)' }} content={TooltipEstado} />
            <Bar
              dataKey="cantidad"
              name="Proyectos"
              radius={[0, 4, 4, 0]}
              maxBarSize={22}
              isAnimationActive={false}
              onClick={(_, indice) => {
                const fila = filas[indice];
                if (fila) alternar(fila.estado);
              }}
            >
              {filas.map((f) => (
                <Cell
                  key={f.estado}
                  cursor="pointer"
                  fill={seleccionado === null || seleccionado === f.estado ? COLOR_PRINCIPAL : COLOR_ATENUADO}
                />
              ))}
              <LabelList dataKey="cantidad" position="right" className="ind-etiqueta-barra" fill="var(--ind-texto-tenue)" />
            </Bar>
          </BarChart>
        </ResponsiveContainer>
      </div>
      <div className="ind-chips" role="group" aria-label="Filtrar la tabla por estado">
        {filas.map((f) => (
          <button
            key={f.estado}
            type="button"
            className={seleccionado === f.estado ? 'ind-chip activo' : 'ind-chip'}
            aria-pressed={seleccionado === f.estado}
            onClick={() => alternar(f.estado)}
            disabled={f.cantidad === 0 && seleccionado !== f.estado}
          >
            {f.etiqueta} · {f.cantidad}
          </button>
        ))}
      </div>
      <p className="muted ind-nota">Hacé clic en una barra o en un estado para filtrar la tabla de proyectos.</p>
    </div>
  );
}

function TooltipEstado({ active, payload }: TooltipContentProps) {
  if (!active || !payload || payload.length === 0) return null;
  const fila = payload[0].payload as Fila;
  return (
    <div className="ind-tooltip">
      <strong>{fila.etiqueta}</strong>
      <div>
        {formatearNumero(fila.cantidad)} {fila.cantidad === 1 ? 'proyecto' : 'proyectos'}
      </div>
      <div className="muted">Clic para filtrar la tabla</div>
    </div>
  );
}
