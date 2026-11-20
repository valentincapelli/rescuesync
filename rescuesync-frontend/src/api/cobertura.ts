import { apiGet, apiPost } from './client';
import type { Emergencia } from '../types/emergencia';
import type { TipoRecurso } from '../types/lote';

export type DecisionCobertura = 'COMPLETA' | 'PARCIAL' | 'SIN_COBERTURA';

export interface Cobertura {
  emergencia: Emergencia;
  fechaCierreConvocatoria: string | null;
  plazoVencido: boolean;
  decision: DecisionCobertura | null;
  republicacionPendiente: boolean;
  resolucionPendiente: boolean;
  sincronizacionPendiente: boolean;
  resolucion: string | null;
  lotes: {
    id: number;
    tipoRecurso: TipoRecurso;
    descripcion: string;
    cantidadRequerida: number;
    unidadMedida: string;
    totalOfrecido: number;
  }[];
}

export function consultarCobertura(id: number): Promise<Cobertura> {
  return apiGet<Cobertura>(`/emergencias/${id}/cobertura`);
}

export function decidirCobertura(id: number, decision: DecisionCobertura): Promise<Emergencia> {
  return apiPost<Emergencia>(`/emergencias/${id}/cobertura`, { decision });
}

export function resolverCobertura(id: number, observacion: string): Promise<Emergencia> {
  return apiPost<Emergencia>(`/emergencias/${id}/cobertura/resolucion`, { observacion });
}

export function sincronizarCobertura(id: number): Promise<Cobertura> {
  return apiPost<Cobertura>(`/emergencias/${id}/cobertura/sincronizar`);
}
