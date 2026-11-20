import { apiGet, apiPost } from './client';
import type { Despliegue, TipoNovedad } from '../types/despliegue';

export function listarDespliegues(emergenciaId: number | null, actualizar = false): Promise<Despliegue[]> {
  const ruta = emergenciaId === null ? '/actividades/mias' : `/emergencias/${emergenciaId}/despliegues`;
  return actualizar ? apiPost(`${ruta}/sincronizar`) : apiGet(ruta);
}

export function registrarSeguimiento(ofertaId: number, tipo: TipoNovedad, observacion: string): Promise<Despliegue> {
  const rutas: Record<TipoNovedad, string> = {
    NOVEDAD: 'seguimientos',
    MONITOREO_COMPLETADO: 'monitoreo/completar',
    FINALIZACION: 'finalizacion',
  };
  return apiPost(`/ofertas/${ofertaId}/${rutas[tipo]}`, { observacion });
}
