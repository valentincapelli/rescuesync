import {
  Bar,
  BarChart,
  CartesianGrid,
  LabelList,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
  type TooltipContentProps,
} from 'recharts';
import type { TiempoEtapa, TiemposEtapas } from '../../types/indicadores';
import { AvisoBonita } from './AvisoBonita';
import { COLOR_EJE, COLOR_GRILLA, COLOR_PRINCIPAL, formatearDuracion, formatearNumero } from './formato';

interface Fila extends TiempoEtapa {
  valor: number;
}

/** Barras horizontales del tiempo promedio por etapa (datos de Bonita). */
export function GraficoTiemposEtapas({ datos }: { datos: TiemposEtapas }) {
  const filas: Fila[] = datos.etapas.map((e) => ({ ...e, valor: e.promedioHoras ?? 0 }));
  const sinMuestras = datos.etapas.every((e) => e.promedioHoras === null);

  return (
    <div>
      <AvisoBonita bonita={datos.bonita} />
      <p className="ind-mini-resumen">
        <span>
          <strong>{formatearNumero(datos.casosAnalizados)}</strong> casos analizados
        </span>
        <span>
          <strong>{formatearNumero(datos.casosFinalizados)}</strong> finalizados
        </span>
        <span>
          <strong>{formatearDuracion(datos.duracionPromedioTotalHoras)}</strong> duración promedio total
        </span>
      </p>
      {datos.bonita.disponible && sinMuestras ? (
        <p className="ind-estado muted">Todavía no hay tareas completadas en Bonita para los filtros elegidos.</p>
      ) : (
        datos.bonita.disponible && (
          <div className="ind-grafico">
            <ResponsiveContainer width="100%" height={280}>
              <BarChart data={filas} layout="vertical" margin={{ top: 0, right: 64, left: 0, bottom: 0 }}>
                <CartesianGrid horizontal={false} stroke={COLOR_GRILLA} />
                <XAxis
                  type="number"
                  tick={{ fill: COLOR_EJE, fontSize: 12 }}
                  tickLine={false}
                  axisLine={{ stroke: COLOR_GRILLA }}
                  tickFormatter={(v: number) => `${formatearNumero(v)} h`}
                />
                <YAxis
                  type="category"
                  dataKey="nombre"
                  width={160}
                  tick={{ fill: COLOR_EJE, fontSize: 12 }}
                  tickLine={false}
                  axisLine={false}
                />
                <Tooltip cursor={{ fill: 'var(--ind-cursor)' }} content={TooltipEtapa} />
                <Bar dataKey="valor" name="Promedio" fill={COLOR_PRINCIPAL} radius={[0, 4, 4, 0]} maxBarSize={22} isAnimationActive={false}>
                  <LabelList
                    dataKey="promedioHoras"
                    position="right"
                    className="ind-etiqueta-barra" fill="var(--ind-texto-tenue)"
                    formatter={(v: unknown) => formatearDuracion(typeof v === 'number' ? v : null)}
                  />
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          </div>
        )
      )}
    </div>
  );
}

function TooltipEtapa({ active, payload }: TooltipContentProps) {
  if (!active || !payload || payload.length === 0) return null;
  const e = payload[0].payload as Fila;
  return (
    <div className="ind-tooltip">
      <strong>{e.nombre}</strong>
      <div className="ind-tooltip-fila">
        <span />
        <span>Promedio</span>
        <span className="num">{formatearDuracion(e.promedioHoras)}</span>
      </div>
      <div className="ind-tooltip-fila">
        <span />
        <span>Mediana</span>
        <span className="num">{formatearDuracion(e.medianaHoras)}</span>
      </div>
      <div className="ind-tooltip-fila">
        <span />
        <span>Mínimo</span>
        <span className="num">{formatearDuracion(e.minimoHoras)}</span>
      </div>
      <div className="ind-tooltip-fila">
        <span />
        <span>Máximo</span>
        <span className="num">{formatearDuracion(e.maximoHoras)}</span>
      </div>
      <div className="ind-tooltip-fila">
        <span />
        <span>Muestras</span>
        <span className="num">
          {formatearNumero(e.muestras)} {e.muestras === 1 ? 'caso' : 'casos'}
        </span>
      </div>
      {e.tareas.length > 0 && <div className="muted ind-tooltip-tareas">Tareas: {e.tareas.join(', ')}</div>}
    </div>
  );
}
