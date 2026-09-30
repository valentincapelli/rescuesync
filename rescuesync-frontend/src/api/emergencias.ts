import { apiGet, apiPost } from './client';
import type { CrearEmergenciaRequest, Emergencia } from '../types/emergencia';

export function crearEmergencia(request: CrearEmergenciaRequest): Promise<Emergencia> {
  return apiPost<Emergencia>('/emergencias', request);
}

export function listarEmergencias(): Promise<Emergencia[]> {
  return apiGet<Emergencia[]>('/emergencias');
}

// "Publicar convocatoria": la emergencia pasa a CONVOCATORIA_PUBLICADA y sus lotes a PUBLICADO.
export function publicarConvocatoria(emergenciaId: number): Promise<Emergencia> {
  return apiPost<Emergencia>(`/emergencias/${emergenciaId}/convocatoria`);
}
