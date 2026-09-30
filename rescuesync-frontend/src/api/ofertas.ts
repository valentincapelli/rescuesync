import { apiPost } from './client';
import type { CrearOfertaRequest, Oferta } from '../types/oferta';

export function crearOferta(loteId: number, request: CrearOfertaRequest): Promise<Oferta> {
  return apiPost<Oferta>(`/lotes/${loteId}/ofertas`, request);
}
