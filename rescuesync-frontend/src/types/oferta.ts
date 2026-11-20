// Espeja EstadoOferta y los DTOs de ofertas del backend
// (com.grupo21.rescuesync.model / dto.ofertas).
import type { EstadoEmergencia, NivelGravedad, TipoDesastre } from './emergencia';
import type { EstadoLote, TipoRecurso } from './lote';
import type { Lote } from './lote';

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

// Espeja CrearOfertaRequest.
// El lote se identifica mediante la URL y la ONG
// se obtiene del usuario autenticado.
export interface CrearOfertaRequest {
  cantidadOfrecida: number;
  observaciones?: string;
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

export interface OfertaPropia {
  oferta: Oferta;
  lote: Lote;
  emergenciaId: number;
  editable: boolean;
}

// Espeja ClasificacionOferta: cómo se adapta la oferta según el perfil del Sistema Nacional.
export type ClasificacionOferta = 'PRINCIPAL' | 'APOYO' | 'SIN_HABILITACION';

export const CLASIFICACION_OFERTA_LABELS: Record<ClasificacionOferta, string> = {
  PRINCIPAL: 'Principal',
  APOYO: 'Apoyo secundario',
  SIN_HABILITACION: 'Sin habilitación',
};

export type NivelCompetencia = 'BASICO' | 'INTERMEDIO' | 'AVANZADO';

export const NIVEL_COMPETENCIA_LABELS: Record<NivelCompetencia, string> = {
  BASICO: 'Básico',
  INTERMEDIO: 'Intermedio',
  AVANZADO: 'Avanzado',
};

// Espeja OfertaEvaluadaResponse.Validacion.
export interface ValidacionOferta {
  clasificacion: ClasificacionOferta;
  nivelCompetencia: NivelCompetencia | null;
  nivelRiesgoMax: NivelGravedad | null;
  certificacionVence: string | null; // yyyy-MM-dd
  observaciones: string | null;
}

// Espeja OfertaEvaluadaResponse.
export interface OfertaEvaluada extends Oferta {
  // Datos incompletos sin validación nacional: no se pueden seleccionar.
  validacion: ValidacionOferta | null;
}

export interface LoteConOfertasEvaluadas {
  id: number;
  tipoRecurso: TipoRecurso;
  descripcion: string;
  cantidadRequerida: number;
  unidadMedida: string;
  estado: EstadoLote;
  // Validadas (principal o apoyo): las únicas que se pueden seleccionar.
  ofertas: OfertaEvaluada[];
  // Evaluadas sin certificación vigente: se informan, no se seleccionan.
  ofertasSinHabilitacion: OfertaEvaluada[];
}

export interface EmergenciaConOfertasEvaluadas {
  id: number;
  tipoDesastre: TipoDesastre;
  nivelGravedad: NivelGravedad;
  zonaAfectada: string;
  descripcion: string;
  municipio: string;
  estado: EstadoEmergencia;
  lotes: LoteConOfertasEvaluadas[];
}

export interface SeleccionarOfertasResponse {
  cantidadSeleccionadas: number;
  cantidadRechazadas: number;
}
