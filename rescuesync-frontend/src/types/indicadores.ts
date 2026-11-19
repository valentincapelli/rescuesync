// Espeja com.grupo21.rescuesync.dto.indicadores del backend (E4, dashboard de indicadores).
// Contrato completo y semántica de cada campo: docs/e4-indicadores-contrato.md.
// Si cambia un record del backend, hay que actualizar esto también.
//
// Convenciones:
// - Fechas: LocalDate -> 'yyyy-MM-dd'; Instant -> ISO-8601 (ej. '2026-11-18T15:30:00Z').
// - Porcentajes en escala 0-100. null = sin datos (denominador 0), no es lo mismo que 0.
// - Duraciones en horas (number decimal).

import type { EstadoEmergencia, NivelGravedad, TipoDesastre } from './emergencia';
import type { TipoRecurso } from './lote';

// ---------- Filtros (query params, todos opcionales) ----------

export interface FiltroIndicadores {
  desde?: string; // 'yyyy-MM-dd', inclusive. Default: 1er día del mes, 11 meses atrás
  hasta?: string; // 'yyyy-MM-dd', inclusive. Default: hoy
  municipioId?: number;
  tipoDesastre?: TipoDesastre;
  tipoRecurso?: TipoRecurso; // solo afecta suministros y consorcios
}

/** Filtro tal como lo aplicó el backend (con las fechas por defecto completadas). */
export interface FiltroAplicado {
  desde: string;
  hasta: string;
  municipioId: number | null;
  tipoDesastre: TipoDesastre | null;
  tipoRecurso: TipoRecurso | null;
}

export type FormatoReporte = 'json' | 'csv' | 'xlsx';

// ---------- Bonita ----------

export interface EstadoFuenteBonita {
  disponible: boolean;
  mensaje: string | null; // motivo si no está disponible
  consultadoEn: string; // Instant
}

export const ETAPAS_EMERGENCIA = [
  'REGISTRO',
  'CONVOCATORIA',
  'EVALUACION',
  'COMPROMISO',
  'EJECUCION',
  'CIERRE',
] as const;
export type EtapaEmergencia = (typeof ETAPAS_EMERGENCIA)[number];

// ---------- GET /indicadores/resumen ----------

export interface ResumenIndicadores {
  generadoEn: string;
  filtro: FiltroAplicado;
  emergenciasTotales: number;
  emergenciasActivas: number;
  proyectosFinalizados: number;
  porcentajeFinalizados: number | null;
  lotesResueltos: number;
  porcentajeResolucionConsorcio: number | null;
  tiempoPromedioResolucionHoras: number | null; // de Bonita
  bonita: EstadoFuenteBonita;
}

// ---------- GET /indicadores/suministros ----------

export interface SuministroMensual {
  mes: string; // 'yyyy-MM'
  tipoRecurso: TipoRecurso;
  unidadMedida: string; // texto libre: no sumar unidades distintas
  cantidad: number;
  ofertas: number;
}

export interface TotalSuministro {
  tipoRecurso: TipoRecurso;
  unidadMedida: string;
  cantidad: number;
  ofertas: number;
}

export interface SuministrosMovilizados {
  series: SuministroMensual[]; // sin filas en cero: los meses sin datos no vienen
  totales: TotalSuministro[];
}

// ---------- GET /indicadores/consorcios ----------

export interface ConsorcioPorTipoRecurso {
  tipoRecurso: TipoRecurso;
  lotesResueltos: number;
  lotesPorConsorcio: number;
  porcentajeConsorcio: number | null;
}

export interface ResolucionConsorcios {
  lotesResueltos: number;
  lotesPorConsorcio: number;
  lotesPorOngUnica: number;
  porcentajeConsorcio: number | null;
  promedioOngsPorConsorcio: number | null;
  porTipoRecurso: ConsorcioPorTipoRecurso[];
}

// ---------- GET /indicadores/proyectos ----------

export interface CantidadPorEstado {
  estado: EstadoEmergencia;
  cantidad: number;
}

export interface ProyectoResumen {
  id: number;
  tipoDesastre: TipoDesastre;
  nivelGravedad: NivelGravedad;
  municipio: string;
  zonaAfectada: string;
  estado: EstadoEmergencia;
  fechaRegistro: string; // Instant
  fechaCierre: string | null; // Instant
  lotes: number;
  porcentajeCobertura: number | null;
  bonitaCaseId: number | null;
}

export interface EstadoProyectos {
  total: number;
  finalizados: number;
  enCurso: number;
  porcentajeFinalizados: number | null;
  porEstado: CantidadPorEstado[]; // siempre los 8 estados, en orden del enum
  proyectos: ProyectoResumen[]; // por fechaRegistro descendente
}

// ---------- GET /indicadores/tiempos-etapas ----------

export interface TiempoEtapa {
  etapa: EtapaEmergencia;
  nombre: string; // etiqueta para la UI
  tareas: string[]; // tareas de Bonita que agrupa
  muestras: number;
  promedioHoras: number | null;
  medianaHoras: number | null;
  minimoHoras: number | null;
  maximoHoras: number | null;
}

export interface TiemposEtapas {
  bonita: EstadoFuenteBonita;
  casosAnalizados: number;
  casosFinalizados: number;
  duracionPromedioTotalHoras: number | null;
  etapas: TiempoEtapa[]; // siempre las 6 etapas, en orden
}

// ---------- GET /indicadores/reporte?formato=json ----------

export interface ReporteIndicadores {
  resumen: ResumenIndicadores;
  suministros: SuministrosMovilizados;
  consorcios: ResolucionConsorcios;
  proyectos: EstadoProyectos;
  tiemposEtapas: TiemposEtapas;
}
