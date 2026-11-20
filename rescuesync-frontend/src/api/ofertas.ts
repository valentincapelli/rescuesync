import { apiGet, apiPost, apiPut } from './client';
import type {
  CrearOfertaRequest,
  EmergenciaConOfertasEvaluadas,
  Oferta,
  OfertaPropia,
  SeleccionarOfertasResponse,
} from '../types/oferta';

export function crearOferta(loteId: number, request: CrearOfertaRequest): Promise<Oferta> {
  return apiPost<Oferta>(`/lotes/${loteId}/ofertas`, request);
}

export function listarOfertasPropias(): Promise<OfertaPropia[]> {
  return apiGet<OfertaPropia[]>('/ofertas/mias');
}

export function editarOferta(loteId: number, ofertaId: number, request: CrearOfertaRequest): Promise<Oferta> {
  return apiPut<Oferta>(`/lotes/${loteId}/ofertas/${ofertaId}`, request);
}

export function obtenerOfertasEvaluadas(): Promise<EmergenciaConOfertasEvaluadas[]> {
  return apiGet<EmergenciaConOfertasEvaluadas[]>('/ofertas/evaluadas');
}

export function seleccionarOfertas(
  emergenciaId: number,
  ofertaIds: number[],
): Promise<SeleccionarOfertasResponse> {
  return apiPut<SeleccionarOfertasResponse>('/ofertas/seleccion', {
    emergenciaId,
    ofertaIds,
  });
}
