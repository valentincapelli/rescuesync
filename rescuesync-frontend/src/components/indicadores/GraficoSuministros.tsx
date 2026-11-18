import { useMemo, useState } from 'react';
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
import type { SuministrosMovilizados } from '../../types/indicadores';
import { TIPO_RECURSO_LABELS, TIPOS_RECURSO, type TipoRecurso } from '../../types/lote';
import { COLOR_EJE, COLOR_GRILLA, colorTipoRecurso, formatearMes, formatearNumero } from './formato';

interface Props {
  datos: SuministrosMovilizados;
  /** Meses 'yyyy-MM' del rango aplicado; los que no vienen en series se completan con 0. */
  meses: string[];
}

type Fila = { mes: string; etiqueta: string } & Partial<Record<TipoRecurso, number>>;

/** Barras apiladas por mes, una serie por tipo de recurso, para una sola unidad de medida. */
export function GraficoSuministros({ datos, meses }: Props) {
  // Unidades ordenadas por cantidad de ofertas (la más usada primero).
  const unidades = useMemo(() => {
    const ofertasPorUnidad = new Map<string, number>();
    for (const t of datos.totales) {
      ofertasPorUnidad.set(t.unidadMedida, (ofertasPorUnidad.get(t.unidadMedida) ?? 0) + t.ofertas);
    }
    for (const s of datos.series) {
      if (!ofertasPorUnidad.has(s.unidadMedida)) ofertasPorUnidad.set(s.unidadMedida, 0);
    }
    return [...ofertasPorUnidad.entries()].sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0])).map(([u]) => u);
  }, [datos]);

  const [elegida, setElegida] = useState<string | null>(null);
  const unidad = elegida !== null && unidades.includes(elegida) ? elegida : (unidades[0] ?? '');

  const tipos = useMemo(
    () => TIPOS_RECURSO.filter((t) => datos.series.some((s) => s.unidadMedida === unidad && s.tipoRecurso === t)),
    [datos, unidad],
  );

  const filas = useMemo<Fila[]>(() => {
    // Si hay meses en series fuera del rango (no debería), se agregan igual.
    const todos = [...new Set([...meses, ...datos.series.map((s) => s.mes)])].sort();
    return todos.map((mes) => {
      const fila: Fila = { mes, etiqueta: formatearMes(mes) };
      for (const t of tipos) fila[t] = 0;
      for (const s of datos.series) {
        if (s.mes === mes && s.unidadMedida === unidad) fila[s.tipoRecurso] = (fila[s.tipoRecurso] ?? 0) + s.cantidad;
      }
      return fila;
    });
  }, [datos, meses, tipos, unidad]);

  const totalesUnidad = datos.totales.filter((t) => t.unidadMedida === unidad);

  return (
    <div>
      <div className="ind-selector-unidad" role="group" aria-label="Unidad de medida">
        <span className="muted">Unidad:</span>
        {unidades.map((u) => (
          <button
            key={u}
            type="button"
            className={u === unidad ? 'ind-chip activo' : 'ind-chip'}
            aria-pressed={u === unidad}
            onClick={() => setElegida(u)}
          >
            {u}
          </button>
        ))}
      </div>
      <div className="ind-grafico">
        <ResponsiveContainer width="100%" height={280}>
          <BarChart data={filas} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
            <CartesianGrid vertical={false} stroke={COLOR_GRILLA} />
            <XAxis dataKey="etiqueta" tick={{ fill: COLOR_EJE, fontSize: 12 }} tickLine={false} axisLine={{ stroke: COLOR_GRILLA }} />
            <YAxis
              tick={{ fill: COLOR_EJE, fontSize: 12 }}
              tickLine={false}
              axisLine={false}
              width={56}
              tickFormatter={(v: number) => formatearNumero(v)}
              allowDecimals={false}
            />
            <Tooltip
              cursor={{ fill: 'var(--ind-cursor)' }}
              content={(props: TooltipContentProps) => <TooltipSuministros {...props} unidad={unidad} />}
            />
            <Legend
              iconType="square"
              wrapperStyle={{ fontSize: 12 }}
              formatter={(valor: string) => <span className="ind-leyenda-texto">{valor}</span>}
            />
            {tipos.map((t, i) => (
              <Bar
                key={t}
                dataKey={t}
                name={TIPO_RECURSO_LABELS[t]}
                stackId="suministros"
                fill={colorTipoRecurso(t)}
                stroke="var(--ind-superficie)"
                strokeWidth={1}
                radius={i === tipos.length - 1 ? [4, 4, 0, 0] : 0}
                maxBarSize={44}
                isAnimationActive={false}
              />
            ))}
          </BarChart>
        </ResponsiveContainer>
      </div>
      {totalesUnidad.length > 0 && (
        <ul className="ind-totales">
          {totalesUnidad.map((t) => (
            <li key={t.tipoRecurso}>
              <span className="ind-marca" style={{ background: colorTipoRecurso(t.tipoRecurso) }} aria-hidden="true" />
              {TIPO_RECURSO_LABELS[t.tipoRecurso]}: <strong>{formatearNumero(t.cantidad)}</strong> {t.unidadMedida} (
              {t.ofertas} {t.ofertas === 1 ? 'oferta' : 'ofertas'})
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function TooltipSuministros({ active, payload, label, unidad }: TooltipContentProps & { unidad: string }) {
  if (!active || !payload || payload.length === 0) return null;
  const items = payload.filter((p) => typeof p.value === 'number' && p.value > 0);
  const total = items.reduce((acc, p) => acc + (p.value as number), 0);
  return (
    <div className="ind-tooltip">
      <strong>{label}</strong>
      {items.length === 0 ? (
        <div className="muted">Sin movimientos</div>
      ) : (
        <>
          {[...items].reverse().map((p) => (
            <div key={String(p.dataKey)} className="ind-tooltip-fila">
              <span className="ind-marca" style={{ background: p.color }} aria-hidden="true" />
              <span>{p.name}</span>
              <span className="num">
                {formatearNumero(p.value as number)} {unidad}
              </span>
            </div>
          ))}
          {items.length > 1 && (
            <div className="ind-tooltip-fila ind-tooltip-total">
              <span />
              <span>Total</span>
              <span className="num">
                {formatearNumero(total)} {unidad}
              </span>
            </div>
          )}
        </>
      )}
    </div>
  );
}
