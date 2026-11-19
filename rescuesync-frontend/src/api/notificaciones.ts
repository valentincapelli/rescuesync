import { apiGet, apiPatch } from './client';

export interface NotificacionResponse {
  id: number;
  titulo: string;
  mensaje: string;
  tipo: 'OFERTA_SELECCIONADA' | 'OFERTA_RECHAZADA';
  fechaCreacion: string;
  leida: boolean;
}

export function obtenerNotificaciones(): Promise<NotificacionResponse[]> {
  return apiGet<NotificacionResponse[]>('/notificaciones');
}

export function contarNoLeidas(): Promise<number> {
  return apiGet<number>('/notificaciones/no-leidas/count');
}

export function marcarComoLeida(id: number): Promise<void> {
  return apiPatch<void>(`/notificaciones/${id}/leida`);
}