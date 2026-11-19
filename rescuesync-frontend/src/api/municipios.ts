import { apiGet } from './client';
import type { Municipio } from '../types/municipio';

/** GET /api/municipios: id y nombre, ordenados por nombre. */
export function listarMunicipios(): Promise<Municipio[]> {
  return apiGet<Municipio[]>('/municipios');
}
