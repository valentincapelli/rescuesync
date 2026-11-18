// Formateo y utilidades compartidas por los bloques del dashboard de indicadores (E4).
// Convenciones del contrato (docs/e4-indicadores-contrato.md, sección 2): porcentajes 0-100,
// null = "Sin datos" (no es 0 %), duraciones en horas decimales.

import { ApiError } from '../../api/client';
import type { EstadoEmergencia } from '../../types/emergencia';
import type { FiltroIndicadores } from '../../types/indicadores';
import type { TipoRecurso } from '../../types/lote';

export const SIN_DATOS = 'Sin datos';

const ZONA_AR = 'America/Argentina/Buenos_Aires';

// Mismo orden que el enum del backend (porEstado llega en este orden).
export const ESTADOS_EMERGENCIA: EstadoEmergencia[] = [
  'REGISTRADA',
  'EN_REVISION',
  'CONVOCATORIA_PUBLICADA',
  'COBERTURA_EVALUADA',
  'COBERTURA_PARCIAL',
  'ADJUDICADA',
  'EN_EJECUCION',
  'CERRADA',
];

const numero1 = new Intl.NumberFormat('es-AR', { maximumFractionDigits: 1 });
const entero = new Intl.NumberFormat('es-AR', { maximumFractionDigits: 0 });

export function formatearNumero(valor: number): string {
  return Number.isInteger(valor) ? entero.format(valor) : numero1.format(valor);
}

export function formatearPorcentaje(valor: number | null | undefined): string {
  return valor === null || valor === undefined ? SIN_DATOS : `${numero1.format(valor)} %`;
}

/** Horas con 1 decimal; si pasa de 48 h se muestra en días. */
export function formatearDuracion(horas: number | null | undefined): string {
  if (horas === null || horas === undefined) return SIN_DATOS;
  if (horas > 48) return `${numero1.format(horas / 24)} días`;
  return `${numero1.format(horas)} h`;
}

/** Instant ISO-8601 -> fecha corta en hora de Argentina. */
export function formatearFecha(instant: string | null): string {
  if (!instant) return '—';
  const fecha = new Date(instant);
  if (Number.isNaN(fecha.getTime())) return instant;
  return fecha.toLocaleDateString('es-AR', { timeZone: ZONA_AR, day: '2-digit', month: '2-digit', year: 'numeric' });
}

/** 'yyyy-MM' -> 'nov 2026'. */
export function formatearMes(mes: string): string {
  const [anio, mesNum] = mes.split('-').map(Number);
  const fecha = new Date(Date.UTC(anio, mesNum - 1, 1));
  return fecha
    .toLocaleDateString('es-AR', { timeZone: 'UTC', month: 'short', year: 'numeric' })
    .replace('.', '')
    .replace(' de ', ' ');
}

/** Hoy en Argentina como 'yyyy-MM-dd'. */
export function hoyArgentina(): string {
  // en-CA formatea como yyyy-MM-dd.
  return new Date().toLocaleDateString('en-CA', { timeZone: ZONA_AR });
}

/** Default del backend para `desde`: 1er día del mes, 11 meses antes de `hasta`. */
export function desdePorDefecto(hasta: string): string {
  const [anio, mes] = hasta.split('-').map(Number);
  const total = anio * 12 + (mes - 1) - 11;
  const anioDesde = Math.floor(total / 12);
  const mesDesde = (total % 12) + 1;
  return `${anioDesde}-${String(mesDesde).padStart(2, '0')}-01`;
}

/** Rango efectivo de un filtro, con los mismos defaults que aplica el backend. */
export function rangoEfectivo(filtro: FiltroIndicadores): { desde: string; hasta: string } {
  const hasta = filtro.hasta ?? hoyArgentina();
  return { desde: filtro.desde ?? desdePorDefecto(hasta), hasta };
}

/** Todos los meses 'yyyy-MM' entre dos fechas 'yyyy-MM-dd', inclusive. */
export function mesesEntre(desde: string, hasta: string): string[] {
  const [anioD, mesD] = desde.split('-').map(Number);
  const [anioH, mesH] = hasta.split('-').map(Number);
  const meses: string[] = [];
  for (let i = anioD * 12 + mesD - 1; i <= anioH * 12 + mesH - 1 && meses.length < 240; i++) {
    meses.push(`${Math.floor(i / 12)}-${String((i % 12) + 1).padStart(2, '0')}`);
  }
  return meses;
}

export function mensajeDeError(err: unknown, fallback: string): string {
  if (err instanceof ApiError) {
    if (err.status === 403) return 'Tu usuario no tiene permiso para consultar indicadores.';
    if (err.status === 401) return 'La sesión expiró. Volvé a iniciar sesión.';
    return err.message;
  }
  return fallback;
}

// ---------- Colores ----------
// Color fijo por entidad (no por posición), para que un filtro no repinte las series.
// Los valores están en IndicadoresPage.css (variables --ind-serie-N, con modo oscuro).

const SLOT_POR_TIPO: Record<TipoRecurso, string> = {
  PERSONAL_VOLUNTARIO: 'var(--ind-serie-1)',
  ALIMENTOS: 'var(--ind-serie-2)',
  AGUA_POTABLE: 'var(--ind-serie-3)',
  MEDICAMENTOS: 'var(--ind-serie-7)',
  ABRIGO_Y_ROPA: 'var(--ind-serie-5)',
  TRANSPORTE: 'var(--ind-serie-6)',
  MAQUINARIA_PESADA: 'var(--ind-serie-4)',
  REFUGIO_TEMPORAL: 'var(--ind-serie-8)',
  OTRO: 'var(--ind-serie-otro)',
};

export function colorTipoRecurso(tipo: TipoRecurso): string {
  return SLOT_POR_TIPO[tipo];
}

export const COLOR_PRINCIPAL = 'var(--ind-serie-1)';
export const COLOR_SECUNDARIO = 'var(--ind-serie-2)';
export const COLOR_ATENUADO = 'var(--ind-atenuado)';
export const COLOR_GRILLA = 'var(--ind-grilla)';
export const COLOR_EJE = 'var(--ind-texto-tenue)';
