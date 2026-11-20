import type { EstadoEmergencia } from './emergencia';
import type { TipoRecurso } from './lote';

export type TipoNovedad = 'NOVEDAD' | 'MONITOREO_COMPLETADO' | 'FINALIZACION';
export interface NovedadDespliegue {
  id: number;
  tipo: TipoNovedad;
  observacion: string;
  autor: string;
  fecha: string;
  confirmada: boolean;
}
export interface Despliegue {
  ofertaId: number;
  emergenciaId: number;
  zonaAfectada: string;
  municipio: string;
  estadoEmergencia: EstadoEmergencia;
  loteId: number;
  tipoRecurso: TipoRecurso;
  descripcion: string;
  ong: string;
  cantidad: number;
  unidadMedida: string;
  comprometidoEn: string;
  monitoreadoEn: string | null;
  finalizadoEn: string | null;
  liberadoEn: string | null;
  sincronizadoEn: string | null;
  puedeMonitorear: boolean;
  puedeFinalizar: boolean;
  pendienteSincronizacion: boolean;
  novedades: NovedadDespliegue[];
}

export interface SeleccionOfertaEstado {
  ofertaId: number;
  ong: string;
  estadoIntegracion: 'PENDIENTE' | 'DESPACHANDO' | 'EN_CICLO' | 'LIBERADA' | 'INCIDENTE';
  detalleIncidente: string | null;
  comprometidoEn: string | null;
  finalizadoEn: string | null;
  liberadoEn: string | null;
}
