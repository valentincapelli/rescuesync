// Espeja los enums de com.grupo21.rescuesync.model del backend.
// Si se agrega/renombra un valor ahí, hay que actualizar esto también.

export const TIPOS_DESASTRE = [
  'INUNDACION',
  'INCENDIO',
  'SISMO',
  'TORMENTA_SEVERA',
  'SEQUIA',
  'DESLIZAMIENTO',
  'OTRO',
] as const;
export type TipoDesastre = (typeof TIPOS_DESASTRE)[number];

export const NIVELES_GRAVEDAD = ['BAJO', 'MEDIO', 'ALTO', 'CRITICO'] as const;
export type NivelGravedad = (typeof NIVELES_GRAVEDAD)[number];

export type EstadoEmergencia =
  | 'REGISTRADA'
  | 'EN_REVISION'
  | 'CONVOCATORIA_PUBLICADA'
  | 'COBERTURA_EVALUADA'
  | 'COBERTURA_PARCIAL'
  | 'ADJUDICADA'
  | 'EN_EJECUCION'
  | 'CERRADA';

// Etiquetas para mostrar en la UI (los <select> siguen mandando el valor del enum).
export const TIPO_DESASTRE_LABELS: Record<TipoDesastre, string> = {
  INUNDACION: 'Inundación',
  INCENDIO: 'Incendio',
  SISMO: 'Sismo',
  TORMENTA_SEVERA: 'Tormenta severa',
  SEQUIA: 'Sequía',
  DESLIZAMIENTO: 'Deslizamiento',
  OTRO: 'Otro',
};

export const NIVEL_GRAVEDAD_LABELS: Record<NivelGravedad, string> = {
  BAJO: 'Bajo',
  MEDIO: 'Medio',
  ALTO: 'Alto',
  CRITICO: 'Crítico',
};

// Espeja CrearEmergenciaRequest del backend.
export interface CrearEmergenciaRequest {
  tipoDesastre: TipoDesastre;
  nivelGravedad: NivelGravedad;
  zonaAfectada: string;
  descripcion: string;
  municipio: string;
}

// Espeja EmergenciaResponse del backend.
export interface Emergencia {
  id: number;
  tipoDesastre: TipoDesastre;
  nivelGravedad: NivelGravedad;
  zonaAfectada: string;
  descripcion: string;
  municipio: string;
  estado: EstadoEmergencia;
  bonitaCaseId: number | null;
  createdAt: string;
}

export const ESTADO_EMERGENCIA_LABELS: Record<EstadoEmergencia, string> = {
  REGISTRADA: 'Registrada',
  EN_REVISION: 'En revisión',
  CONVOCATORIA_PUBLICADA: 'Convocatoria publicada',
  COBERTURA_EVALUADA: 'Cobertura evaluada',
  COBERTURA_PARCIAL: 'Cobertura parcial',
  ADJUDICADA: 'Adjudicada',
  EN_EJECUCION: 'En ejecución',
  CERRADA: 'Cerrada',
};

// Mientras la emergencia esté en estos estados, el CCR puede cargar/editar/borrar lotes.
export const ESTADOS_DESGLOSE_ABIERTO: EstadoEmergencia[] = ['REGISTRADA', 'EN_REVISION'];
