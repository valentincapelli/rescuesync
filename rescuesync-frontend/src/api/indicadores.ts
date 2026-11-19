import { API_URL, ApiError, apiGet } from './client';
import type {
  EstadoProyectos,
  FiltroIndicadores,
  FormatoReporte,
  ReporteIndicadores,
  ResolucionConsorcios,
  ResumenIndicadores,
  SuministrosMovilizados,
  TiemposEtapas,
} from '../types/indicadores';

// Endpoints de /api/indicadores (rol DIRECTIVO). Contrato: docs/e4-indicadores-contrato.md.

export function toQuery(filtro: FiltroIndicadores = {}): string {
  const params = new URLSearchParams();
  for (const [clave, valor] of Object.entries(filtro)) {
    if (valor !== undefined && valor !== null && valor !== '') {
      params.set(clave, String(valor));
    }
  }
  const qs = params.toString();
  return qs ? `?${qs}` : '';
}

export function obtenerResumen(filtro?: FiltroIndicadores): Promise<ResumenIndicadores> {
  return apiGet<ResumenIndicadores>(`/indicadores/resumen${toQuery(filtro)}`);
}

export function obtenerSuministros(filtro?: FiltroIndicadores): Promise<SuministrosMovilizados> {
  return apiGet<SuministrosMovilizados>(`/indicadores/suministros${toQuery(filtro)}`);
}

export function obtenerConsorcios(filtro?: FiltroIndicadores): Promise<ResolucionConsorcios> {
  return apiGet<ResolucionConsorcios>(`/indicadores/consorcios${toQuery(filtro)}`);
}

export function obtenerProyectos(filtro?: FiltroIndicadores): Promise<EstadoProyectos> {
  return apiGet<EstadoProyectos>(`/indicadores/proyectos${toQuery(filtro)}`);
}

export function obtenerTiemposEtapas(filtro?: FiltroIndicadores): Promise<TiemposEtapas> {
  return apiGet<TiemposEtapas>(`/indicadores/tiempos-etapas${toQuery(filtro)}`);
}

export function obtenerReporte(filtro?: FiltroIndicadores): Promise<ReporteIndicadores> {
  return apiGet<ReporteIndicadores>(`/indicadores/reporte${toQuery(filtro)}`);
}

/**
 * Descarga el reporte como archivo. Devuelve el Blob; quien llama decide cómo guardarlo
 * (ej. URL.createObjectURL + <a download>). El backend manda el nombre sugerido en Content-Disposition
 * (indicadores_<desde>_<hasta>.<ext>); el front arma el mismo nombre con el filtro aplicado.
 */
export async function descargarReporte(
  formato: FormatoReporte,
  filtro?: FiltroIndicadores,
): Promise<Blob> {
  const qs = toQuery(filtro);
  const separador = qs ? '&' : '?';
  const res = await fetch(`${API_URL}/indicadores/reporte${qs}${separador}formato=${formato}`, {
    credentials: 'include',
  });
  if (!res.ok) {
    let mensaje = `No se pudo descargar el reporte (${res.status})`;
    try {
      const body = (await res.json()) as { message?: string };
      mensaje = body.message ?? mensaje;
    } catch {
      // cuerpo no JSON: se usa el mensaje por defecto
    }
    throw new ApiError(mensaje, res.status);
  }
  return res.blob();
}
