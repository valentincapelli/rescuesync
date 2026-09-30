// Espeja EstadoOferta y los DTOs de ofertas del backend
// (com.grupo21.rescuesync.model / dto.ofertas).
export type EstadoOferta =
  | 'PENDIENTE'
  | 'EVALUADA'
  | 'SELECCIONADA'
  | 'RECHAZADA'
  | 'COMPROMETIDA'
  | 'FINALIZADA';

export const ESTADO_OFERTA_LABELS: Record<EstadoOferta, string> = {
  PENDIENTE: 'Pendiente',
  EVALUADA: 'Evaluada',
  SELECCIONADA: 'Seleccionada',
  RECHAZADA: 'Rechazada',
  COMPROMETIDA: 'Comprometida',
  FINALIZADA: 'Finalizada',
};

// Espeja CrearOfertaRequest. El lote se envía en la URL.
export interface CrearOfertaRequest {
  ongNombre: string;
  cantidadOfrecida: number;
  observaciones: string | null;
}

// Espeja OfertaResponse.
export interface Oferta {
  id: number;
  loteId: number;
  ongNombre: string;
  cantidadOfrecida: number;
  observaciones: string | null;
  estado: EstadoOferta;
}
