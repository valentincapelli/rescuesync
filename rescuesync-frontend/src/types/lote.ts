// Espeja TipoRecurso / EstadoLote y los DTOs de lotes del backend
// (com.grupo21.rescuesync.model / dto). Si cambian allá, actualizar acá.

export const TIPOS_RECURSO = [
  'PERSONAL_VOLUNTARIO',
  'ALIMENTOS',
  'AGUA_POTABLE',
  'MEDICAMENTOS',
  'ABRIGO_Y_ROPA',
  'TRANSPORTE',
  'MAQUINARIA_PESADA',
  'REFUGIO_TEMPORAL',
  'OTRO',
] as const;
export type TipoRecurso = (typeof TIPOS_RECURSO)[number];

export type EstadoLote = 'BORRADOR' | 'PUBLICADO' | 'CUBIERTO' | 'PARCIALMENTE_CUBIERTO' | 'REFORMULADO' | 'CERRADO';

export const TIPO_RECURSO_LABELS: Record<TipoRecurso, string> = {
  PERSONAL_VOLUNTARIO: 'Personal voluntario',
  ALIMENTOS: 'Alimentos',
  AGUA_POTABLE: 'Agua potable',
  MEDICAMENTOS: 'Medicamentos',
  ABRIGO_Y_ROPA: 'Abrigo y ropa',
  TRANSPORTE: 'Transporte',
  MAQUINARIA_PESADA: 'Maquinaria pesada',
  REFUGIO_TEMPORAL: 'Refugio temporal',
  OTRO: 'Otro',
};

// Sugerencias para el campo "unidad de medida" (sigue siendo texto libre, ver plan.md P9).
export const UNIDADES_SUGERIDAS: Record<TipoRecurso, string[]> = {
  PERSONAL_VOLUNTARIO: ['personas', 'equipos'],
  ALIMENTOS: ['raciones', 'kg', 'módulos'],
  AGUA_POTABLE: ['litros', 'bidones', 'packs'],
  MEDICAMENTOS: ['unidades', 'botiquines', 'cajas'],
  ABRIGO_Y_ROPA: ['prendas', 'frazadas', 'bolsas'],
  TRANSPORTE: ['vehículos', 'viajes'],
  MAQUINARIA_PESADA: ['máquinas', 'horas'],
  REFUGIO_TEMPORAL: ['plazas', 'carpas'],
  OTRO: ['unidades'],
};

export const ESTADO_LOTE_LABELS: Record<EstadoLote, string> = {
  BORRADOR: 'Borrador',
  PUBLICADO: 'Publicado',
  CUBIERTO: 'Cubierto',
  PARCIALMENTE_CUBIERTO: 'Parcialmente cubierto',
  REFORMULADO: 'Reformulado',
  CERRADO: 'Cerrado',
};

// Espeja CrearLoteRequest (se usa para crear y para editar).
export interface LoteRequest {
  tipoRecurso: TipoRecurso;
  descripcion: string;
  cantidadRequerida: number;
  unidadMedida: string;
}

// Espeja LoteResponse.
export interface Lote {
  id: number;
  emergenciaId: number;
  tipoRecurso: TipoRecurso;
  descripcion: string;
  cantidadRequerida: number;
  unidadMedida: string;
  estado: EstadoLote;
}
