import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
  type TooltipContentProps,
} from 'recharts';
import type { ConsorcioPorTipoRecurso, ResolucionConsorcios } from '../../types/indicadores';
import { TIPO_RECURSO_LABELS } from '../../types/lote';
import {
  COLOR_EJE,
  COLOR_GRILLA,
  COLOR_PRINCIPAL,
  COLOR_SECUNDARIO,
  formatearNumero,
  formatearPorcentaje,
} from './formato';

interface Fila extends ConsorcioPorTipoRecurso {
  etiqueta: string;
  ongUnica: number;
}

/** Lotes resueltos por tipo de recurso, apilados en "por consorcio" y "por ONG única". */
export function GraficoConsorcios({ datos }: { datos: ResolucionConsorcios }) {
  const filas: Fila[] = datos.porTipoRecurso.map((c) => ({
    ...c,
    etiqueta: TIPO_RECURSO_LABELS[c.tipoRecurso],
    ongUnica: c.lotesResueltos - c.lotesPorConsorcio,
  }));
  const alto = Math.max(160, filas.length * 44 + 60);

  return (
    <div>
      <p className="ind-mini-resumen">
        <span>
          <strong>{formatearNumero(datos.lotesResueltos)}</strong> lotes resueltos
        </span>
        <span>
          <strong>{formatearPorcentaje(datos.porcentajeConsorcio)}</strong> por consorcio
        </span>
        <span>
          <strong>{datos.promedioOngsPorConsorcio === null ? 'Sin datos' : formatearNumero(datos.promedioOngsPorConsorcio)}</strong>{' '}
          ONGs promedio por consorcio
        </span>
      </p>
      <div className="ind-grafico">
        <ResponsiveContainer width="100%" height={alto}>
          <BarChart data={filas} layout="vertical" margin={{ top: 0, right: 16, left: 0, bottom: 0 }}>
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
              width={130}
              tick={{ fill: COLOR_EJE, fontSize: 12 }}
              tickLine={false}
              axisLine={false}
            />
            <Tooltip cursor={{ fill: 'var(--ind-cursor)' }} content={TooltipConsorcio} />
            <Legend
              iconType="square"
              wrapperStyle={{ fontSize: 12 }}
              formatter={(valor: string) => <span className="ind-leyenda-texto">{valor}</span>}
            />
            <Bar
              dataKey="lotesPorConsorcio"
              name="Por consorcio (2+ ONGs)"
              stackId="lotes"
              fill={COLOR_PRINCIPAL}
              stroke="var(--ind-superficie)"
              strokeWidth={1}
              maxBarSize={24}
              isAnimationActive={false}
            />
            <Bar
              dataKey="ongUnica"
              name="Por una sola ONG"
              stackId="lotes"
              fill={COLOR_SECUNDARIO}
              stroke="var(--ind-superficie)"
              strokeWidth={1}
              radius={[0, 4, 4, 0]}
              maxBarSize={24}
              isAnimationActive={false}
            />
          </BarChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
}

function TooltipConsorcio({ active, payload }: TooltipContentProps) {
  if (!active || !payload || payload.length === 0) return null;
  const fila = payload[0].payload as Fila;
  return (
    <div className="ind-tooltip">
      <strong>{fila.etiqueta}</strong>
      <div className="ind-tooltip-fila">
        <span className="ind-marca" style={{ background: COLOR_PRINCIPAL }} aria-hidden="true" />
        <span>Por consorcio</span>
        <span className="num">{formatearNumero(fila.lotesPorConsorcio)}</span>
      </div>
      <div className="ind-tooltip-fila">
        <span className="ind-marca" style={{ background: COLOR_SECUNDARIO }} aria-hidden="true" />
        <span>Por una sola ONG</span>
        <span className="num">{formatearNumero(fila.ongUnica)}</span>
      </div>
      <div className="ind-tooltip-fila ind-tooltip-total">
        <span />
        <span>% por consorcio</span>
        <span className="num">{formatearPorcentaje(fila.porcentajeConsorcio)}</span>
      </div>
    </div>
  );
}
