import { apiPost } from './client';
import type { CrearEmergenciaRequest, Emergencia } from '../types/emergencia';

export function crearEmergencia(request: CrearEmergenciaRequest): Promise<Emergencia> {
  return apiPost<Emergencia>('/emergencias', request);
}
