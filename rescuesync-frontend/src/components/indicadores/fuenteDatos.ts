// Capa fina entre el dashboard y los datos: con VITE_INDICADORES_MOCK=true usa los mocks de
// src/mocks/indicadores.ts (con una demora corta para ver los estados de carga); si no, llama
// a la API real de src/api/indicadores.ts.

import {
  descargarReporte,
  obtenerConsorcios,
  obtenerProyectos,
  obtenerResumen,
  obtenerSuministros,
  obtenerTiemposEtapas,
} from '../../api/indicadores';
import { listarMunicipios } from '../../api/municipios';
import {
  mockConsorcios,
  mockDescargarReporte,
  mockMunicipios,
  mockProyectos,
  mockResumen,
  mockSuministros,
  mockTiemposEtapas,
} from '../../mocks/indicadores';
import type {
  EstadoProyectos,
  FiltroIndicadores,
  FormatoReporte,
  ResolucionConsorcios,
  ResumenIndicadores,
  SuministrosMovilizados,
  TiemposEtapas,
} from '../../types/indicadores';
import type { Municipio } from '../../types/municipio';

export const USA_MOCKS = import.meta.env.VITE_INDICADORES_MOCK === 'true';

export interface FuenteIndicadores {
  resumen: (filtro: FiltroIndicadores) => Promise<ResumenIndicadores>;
  suministros: (filtro: FiltroIndicadores) => Promise<SuministrosMovilizados>;
  consorcios: (filtro: FiltroIndicadores) => Promise<ResolucionConsorcios>;
  proyectos: (filtro: FiltroIndicadores) => Promise<EstadoProyectos>;
  tiemposEtapas: (filtro: FiltroIndicadores) => Promise<TiemposEtapas>;
  descargar: (formato: FormatoReporte, filtro: FiltroIndicadores) => Promise<Blob>;
  /** Opciones del filtro por municipio. */
  municipios: () => Promise<Municipio[]>;
}

function conDemora<T>(producir: () => T, ms: number): Promise<T> {
  return new Promise((resolve, reject) => {
    setTimeout(() => {
      try {
        resolve(producir());
      } catch (err) {
        reject(err);
      }
    }, ms);
  });
}

const fuenteMock: FuenteIndicadores = {
  resumen: (f) => conDemora(() => mockResumen(f), 300),
  suministros: (f) => conDemora(() => mockSuministros(f), 500),
  consorcios: (f) => conDemora(() => mockConsorcios(f), 450),
  proyectos: (f) => conDemora(() => mockProyectos(f), 400),
  // Bonita suele ser la fuente más lenta.
  tiemposEtapas: (f) => conDemora(() => mockTiemposEtapas(f), 900),
  descargar: (formato, f) => conDemora(() => mockDescargarReporte(formato, f), 300),
  municipios: () => conDemora(mockMunicipios, 150),
};

const fuenteApi: FuenteIndicadores = {
  resumen: obtenerResumen,
  suministros: obtenerSuministros,
  consorcios: obtenerConsorcios,
  proyectos: obtenerProyectos,
  tiemposEtapas: obtenerTiemposEtapas,
  descargar: descargarReporte,
  municipios: listarMunicipios,
};

export const fuenteIndicadores: FuenteIndicadores = USA_MOCKS ? fuenteMock : fuenteApi;
