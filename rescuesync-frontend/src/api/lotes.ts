import { apiDelete, apiGet, apiPost, apiPut } from './client';
import type { Lote, LoteRequest } from '../types/lote';

const base = (emergenciaId: number) => `/emergencias/${emergenciaId}/lotes`;

export function listarLotes(emergenciaId: number): Promise<Lote[]> {
  return apiGet<Lote[]>(base(emergenciaId));
}

export function crearLote(emergenciaId: number, request: LoteRequest): Promise<Lote> {
  return apiPost<Lote>(base(emergenciaId), request);
}

export function editarLote(emergenciaId: number, loteId: number, request: LoteRequest): Promise<Lote> {
  return apiPut<Lote>(`${base(emergenciaId)}/${loteId}`, request);
}

export function eliminarLote(emergenciaId: number, loteId: number): Promise<void> {
  return apiDelete(`${base(emergenciaId)}/${loteId}`);
}
