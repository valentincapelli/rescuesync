// Datos simulados del dashboard de indicadores (E4), para trabajar con el backend en stub.
// Se activan con VITE_INDICADORES_MOCK=true (ver README, "Dashboard de indicadores").
// Cumplen exactamente los tipos de src/types/indicadores.ts y aplican los filtros de forma
// aproximada: fechas y tipo de recurso en suministros/consorcios; fechas, municipio y tipo
// de desastre en proyectos.
//
// Valores especiales de municipioId para probar estados de la UI (no filtran por municipio):
// - 900: Bonita no disponible (tiempos sin datos y aviso en el resumen).
// - 901: /consorcios responde 500 (un bloque con error, el resto sigue andando).

import { ApiError } from '../api/client';
import type { EstadoEmergencia } from '../types/emergencia';
import type {
  CantidadPorEstado,
  ConsorcioPorTipoRecurso,
  EstadoFuenteBonita,
  EstadoProyectos,
  FiltroAplicado,
  FiltroIndicadores,
  FormatoReporte,
  ProyectoResumen,
  ReporteIndicadores,
  ResolucionConsorcios,
  ResumenIndicadores,
  SuministroMensual,
  SuministrosMovilizados,
  TiempoEtapa,
  TiemposEtapas,
  TotalSuministro,
} from '../types/indicadores';
import { TIPOS_RECURSO } from '../types/lote';

export const MUNICIPIO_MOCK_BONITA_CAIDO = 900;
export const MUNICIPIO_MOCK_ERROR_CONSORCIOS = 901;

const ESTADOS: EstadoEmergencia[] = [
  'REGISTRADA',
  'EN_REVISION',
  'CONVOCATORIA_PUBLICADA',
  'COBERTURA_EVALUADA',
  'COBERTURA_PARCIAL',
  'ADJUDICADA',
  'EN_EJECUCION',
  'CERRADA',
];

// ---------- Datos base ----------

interface ProyectoMock extends ProyectoResumen {
  municipioId: number;
}

export const proyectosMock: ProyectoMock[] = [
  { id: 1, municipioId: 1, tipoDesastre: 'INUNDACION', nivelGravedad: 'CRITICO', municipio: 'La Plata', zonaAfectada: 'Barrio El Mondongo', estado: 'CERRADA', fechaRegistro: '2026-04-08T13:20:00Z', fechaCierre: '2026-05-20T18:05:00Z', lotes: 4, porcentajeCobertura: 100, bonitaCaseId: 1001 },
  { id: 2, municipioId: 2, tipoDesastre: 'INUNDACION', nivelGravedad: 'ALTO', municipio: 'Berisso', zonaAfectada: 'Villa Argüello', estado: 'CERRADA', fechaRegistro: '2026-04-22T09:45:00Z', fechaCierre: '2026-06-02T15:30:00Z', lotes: 3, porcentajeCobertura: 92.5, bonitaCaseId: 1002 },
  { id: 3, municipioId: 4, tipoDesastre: 'INCENDIO', nivelGravedad: 'MEDIO', municipio: 'Quilmes', zonaAfectada: 'Reserva costera', estado: 'EN_EJECUCION', fechaRegistro: '2026-05-14T20:10:00Z', fechaCierre: null, lotes: 2, porcentajeCobertura: 100, bonitaCaseId: 1003 },
  { id: 4, municipioId: 3, tipoDesastre: 'TORMENTA_SEVERA', nivelGravedad: 'ALTO', municipio: 'Ensenada', zonaAfectada: 'Punta Lara', estado: 'ADJUDICADA', fechaRegistro: '2026-06-03T11:00:00Z', fechaCierre: null, lotes: 5, porcentajeCobertura: 87, bonitaCaseId: 1004 },
  { id: 5, municipioId: 5, tipoDesastre: 'SEQUIA', nivelGravedad: 'MEDIO', municipio: 'Bahía Blanca', zonaAfectada: 'Zona rural norte', estado: 'CERRADA', fechaRegistro: '2026-06-19T14:25:00Z', fechaCierre: '2026-08-11T12:00:00Z', lotes: 2, porcentajeCobertura: 100, bonitaCaseId: 1005 },
  { id: 6, municipioId: 1, tipoDesastre: 'TORMENTA_SEVERA', nivelGravedad: 'BAJO', municipio: 'La Plata', zonaAfectada: 'Tolosa', estado: 'COBERTURA_PARCIAL', fechaRegistro: '2026-08-05T16:40:00Z', fechaCierre: null, lotes: 3, porcentajeCobertura: 54.3, bonitaCaseId: 1006 },
  { id: 7, municipioId: 4, tipoDesastre: 'INUNDACION', nivelGravedad: 'ALTO', municipio: 'Quilmes', zonaAfectada: 'Bernal Oeste', estado: 'COBERTURA_EVALUADA', fechaRegistro: '2026-08-27T08:15:00Z', fechaCierre: null, lotes: 4, porcentajeCobertura: 71.8, bonitaCaseId: 1007 },
  { id: 8, municipioId: 2, tipoDesastre: 'DESLIZAMIENTO', nivelGravedad: 'CRITICO', municipio: 'Berisso', zonaAfectada: 'Isla Paulino', estado: 'EN_EJECUCION', fechaRegistro: '2026-09-10T22:30:00Z', fechaCierre: null, lotes: 2, porcentajeCobertura: 100, bonitaCaseId: 1008 },
  { id: 9, municipioId: 5, tipoDesastre: 'INCENDIO', nivelGravedad: 'ALTO', municipio: 'Bahía Blanca', zonaAfectada: 'Parque industrial', estado: 'CONVOCATORIA_PUBLICADA', fechaRegistro: '2026-10-02T10:05:00Z', fechaCierre: null, lotes: 3, porcentajeCobertura: 18.6, bonitaCaseId: 1009 },
  { id: 10, municipioId: 3, tipoDesastre: 'INUNDACION', nivelGravedad: 'MEDIO', municipio: 'Ensenada', zonaAfectada: 'Barrio Campamento', estado: 'CONVOCATORIA_PUBLICADA', fechaRegistro: '2026-10-21T17:50:00Z', fechaCierre: null, lotes: 2, porcentajeCobertura: 0, bonitaCaseId: 1010 },
  { id: 11, municipioId: 1, tipoDesastre: 'SISMO', nivelGravedad: 'BAJO', municipio: 'La Plata', zonaAfectada: 'Casco urbano', estado: 'EN_REVISION', fechaRegistro: '2026-11-04T12:35:00Z', fechaCierre: null, lotes: 1, porcentajeCobertura: 0, bonitaCaseId: 1011 },
  { id: 12, municipioId: 4, tipoDesastre: 'OTRO', nivelGravedad: 'MEDIO', municipio: 'Quilmes', zonaAfectada: 'Ezpeleta', estado: 'REGISTRADA', fechaRegistro: '2026-11-15T09:00:00Z', fechaCierre: null, lotes: 0, porcentajeCobertura: null, bonitaCaseId: 1012 },
  { id: 13, municipioId: 2, tipoDesastre: 'INUNDACION', nivelGravedad: 'BAJO', municipio: 'Berisso', zonaAfectada: 'Los Talas', estado: 'REGISTRADA', fechaRegistro: '2026-11-17T19:20:00Z', fechaCierre: null, lotes: 0, porcentajeCobertura: null, bonitaCaseId: null },
];

// Julio no tiene movimientos a propósito: la UI tiene que completar ese mes con 0.
export const seriesMock: SuministroMensual[] = [
  { mes: '2026-04', tipoRecurso: 'ALIMENTOS', unidadMedida: 'unidades', cantidad: 1200, ofertas: 3 },
  { mes: '2026-04', tipoRecurso: 'AGUA_POTABLE', unidadMedida: 'litros', cantidad: 8000, ofertas: 2 },
  { mes: '2026-05', tipoRecurso: 'ALIMENTOS', unidadMedida: 'unidades', cantidad: 2500, ofertas: 4 },
  { mes: '2026-05', tipoRecurso: 'AGUA_POTABLE', unidadMedida: 'litros', cantidad: 12000, ofertas: 3 },
  { mes: '2026-05', tipoRecurso: 'MEDICAMENTOS', unidadMedida: 'unidades', cantidad: 340, ofertas: 2 },
  { mes: '2026-06', tipoRecurso: 'ALIMENTOS', unidadMedida: 'unidades', cantidad: 900, ofertas: 2 },
  { mes: '2026-06', tipoRecurso: 'MEDICAMENTOS', unidadMedida: 'unidades', cantidad: 150, ofertas: 1 },
  { mes: '2026-06', tipoRecurso: 'ABRIGO_Y_ROPA', unidadMedida: 'unidades', cantidad: 600, ofertas: 2 },
  { mes: '2026-08', tipoRecurso: 'AGUA_POTABLE', unidadMedida: 'litros', cantidad: 5000, ofertas: 1 },
  { mes: '2026-08', tipoRecurso: 'ABRIGO_Y_ROPA', unidadMedida: 'unidades', cantidad: 1100, ofertas: 3 },
  { mes: '2026-09', tipoRecurso: 'ALIMENTOS', unidadMedida: 'unidades', cantidad: 1800, ofertas: 3 },
  { mes: '2026-09', tipoRecurso: 'AGUA_POTABLE', unidadMedida: 'litros', cantidad: 15000, ofertas: 4 },
  { mes: '2026-09', tipoRecurso: 'MEDICAMENTOS', unidadMedida: 'unidades', cantidad: 420, ofertas: 2 },
  { mes: '2026-10', tipoRecurso: 'ALIMENTOS', unidadMedida: 'unidades', cantidad: 700, ofertas: 1 },
  { mes: '2026-10', tipoRecurso: 'ABRIGO_Y_ROPA', unidadMedida: 'unidades', cantidad: 450, ofertas: 2 },
  { mes: '2026-11', tipoRecurso: 'AGUA_POTABLE', unidadMedida: 'litros', cantidad: 3000, ofertas: 1 },
  { mes: '2026-11', tipoRecurso: 'MEDICAMENTOS', unidadMedida: 'unidades', cantidad: 90, ofertas: 1 },
];

interface ConsorcioMock extends ConsorcioPorTipoRecurso {
  ongsEnConsorcios: number; // suma de ONGs distintas de los lotes por consorcio
}

const consorciosMock: ConsorcioMock[] = [
  { tipoRecurso: 'ALIMENTOS', lotesResueltos: 6, lotesPorConsorcio: 4, porcentajeConsorcio: 66.7, ongsEnConsorcios: 10 },
  { tipoRecurso: 'AGUA_POTABLE', lotesResueltos: 4, lotesPorConsorcio: 1, porcentajeConsorcio: 25, ongsEnConsorcios: 2 },
  { tipoRecurso: 'MEDICAMENTOS', lotesResueltos: 3, lotesPorConsorcio: 2, porcentajeConsorcio: 66.7, ongsEnConsorcios: 5 },
  { tipoRecurso: 'ABRIGO_Y_ROPA', lotesResueltos: 2, lotesPorConsorcio: 0, porcentajeConsorcio: 0, ongsEnConsorcios: 0 },
];

const etapasMock: TiempoEtapa[] = [
  { etapa: 'REGISTRO', nombre: 'Registro y desglose', tareas: ['Registrar emergencia', 'Revisar y desglosar emergencia'], muestras: 12, promedioHoras: 6.4, medianaHoras: 4.5, minimoHoras: 0.8, maximoHoras: 22.1 },
  { etapa: 'CONVOCATORIA', nombre: 'Convocatoria', tareas: ['Publicar convocatoria', 'Esperar /cargar / editar ofertas'], muestras: 10, promedioHoras: 96.3, medianaHoras: 84, minimoHoras: 48.5, maximoHoras: 170.2 },
  { etapa: 'EVALUACION', nombre: 'Evaluación y selección', tareas: ['Evaluar cobertura de lotes', 'Evaluar ofertas', 'Resolver cobertura incompleta', 'Seleccionar oferta'], muestras: 8, promedioHoras: 31.7, medianaHoras: 26.2, minimoHoras: 9.4, maximoHoras: 75.6 },
  { etapa: 'COMPROMISO', nombre: 'Compromiso de recursos', tareas: ['Comprometer recursos', 'Notificar a ONG', 'Recibir notificación'], muestras: 6, promedioHoras: 14.2, medianaHoras: 12, minimoHoras: 3.1, maximoHoras: 30.5 },
  { etapa: 'EJECUCION', nombre: 'Ejecución', tareas: ['Monitorear despliegue', 'Finalizar actividad'], muestras: 5, promedioHoras: 410.8, medianaHoras: 388, minimoHoras: 192.4, maximoHoras: 702.9 },
  { etapa: 'CIERRE', nombre: 'Cierre', tareas: ['Liberar recursos'], muestras: 3, promedioHoras: 8.9, medianaHoras: 7.5, minimoHoras: 2.2, maximoHoras: 17 },
];

// ---------- Aplicación de filtros ----------

const ZONA_AR = 'America/Argentina/Buenos_Aires';

function hoy(): string {
  return new Date().toLocaleDateString('en-CA', { timeZone: ZONA_AR });
}

function desdeDefecto(hasta: string): string {
  const [anio, mes] = hasta.split('-').map(Number);
  const total = anio * 12 + (mes - 1) - 11;
  return `${Math.floor(total / 12)}-${String((total % 12) + 1).padStart(2, '0')}-01`;
}

function normalizar(filtro: FiltroIndicadores = {}): FiltroAplicado {
  const hasta = filtro.hasta ?? hoy();
  const desde = filtro.desde ?? desdeDefecto(hasta);
  if (desde > hasta) {
    throw new ApiError('La fecha "desde" no puede ser posterior a "hasta".', 400);
  }
  return {
    desde,
    hasta,
    municipioId: filtro.municipioId ?? null,
    tipoDesastre: filtro.tipoDesastre ?? null,
    tipoRecurso: filtro.tipoRecurso ?? null,
  };
}

function esEspecial(municipioId: number | null): boolean {
  return municipioId === MUNICIPIO_MOCK_BONITA_CAIDO || municipioId === MUNICIPIO_MOCK_ERROR_CONSORCIOS;
}

function redondear(valor: number): number {
  return Math.round(valor * 10) / 10;
}

function porcentaje(parte: number, total: number): number | null {
  return total === 0 ? null : redondear((parte / total) * 100);
}

function fechaAr(instant: string): string {
  return new Date(instant).toLocaleDateString('en-CA', { timeZone: ZONA_AR });
}

function estadoBonita(filtro: FiltroAplicado): EstadoFuenteBonita {
  if (filtro.municipioId === MUNICIPIO_MOCK_BONITA_CAIDO) {
    return {
      disponible: false,
      mensaje: 'No se pudo consultar Bonita en este momento.',
      consultadoEn: new Date().toISOString(),
    };
  }
  return { disponible: true, mensaje: null, consultadoEn: new Date().toISOString() };
}

export function mockSuministros(filtro?: FiltroIndicadores): SuministrosMovilizados {
  const f = normalizar(filtro);
  const mesDesde = f.desde.slice(0, 7);
  const mesHasta = f.hasta.slice(0, 7);
  const series = seriesMock.filter(
    (s) => s.mes >= mesDesde && s.mes <= mesHasta && (f.tipoRecurso === null || s.tipoRecurso === f.tipoRecurso),
  );
  const totales: TotalSuministro[] = [];
  for (const tipo of TIPOS_RECURSO) {
    const unidades = [...new Set(series.filter((s) => s.tipoRecurso === tipo).map((s) => s.unidadMedida))];
    for (const unidad of unidades) {
      const filas = series.filter((s) => s.tipoRecurso === tipo && s.unidadMedida === unidad);
      totales.push({
        tipoRecurso: tipo,
        unidadMedida: unidad,
        cantidad: filas.reduce((acc, s) => acc + s.cantidad, 0),
        ofertas: filas.reduce((acc, s) => acc + s.ofertas, 0),
      });
    }
  }
  return { series, totales };
}

export function mockConsorcios(filtro?: FiltroIndicadores): ResolucionConsorcios {
  const f = normalizar(filtro);
  if (f.municipioId === MUNICIPIO_MOCK_ERROR_CONSORCIOS) {
    throw new ApiError('Error simulado al calcular los consorcios.', 500);
  }
  const filas = consorciosMock.filter((c) => f.tipoRecurso === null || c.tipoRecurso === f.tipoRecurso);
  const lotesResueltos = filas.reduce((acc, c) => acc + c.lotesResueltos, 0);
  const lotesPorConsorcio = filas.reduce((acc, c) => acc + c.lotesPorConsorcio, 0);
  const ongs = filas.reduce((acc, c) => acc + c.ongsEnConsorcios, 0);
  return {
    lotesResueltos,
    lotesPorConsorcio,
    lotesPorOngUnica: lotesResueltos - lotesPorConsorcio,
    porcentajeConsorcio: porcentaje(lotesPorConsorcio, lotesResueltos),
    promedioOngsPorConsorcio: lotesPorConsorcio === 0 ? null : redondear(ongs / lotesPorConsorcio),
    porTipoRecurso: filas.map(({ ongsEnConsorcios: _omitido, ...fila }) => fila),
  };
}

function proyectosFiltrados(f: FiltroAplicado): ProyectoResumen[] {
  return proyectosMock
    .filter((p) => {
      const fecha = fechaAr(p.fechaRegistro);
      return (
        fecha >= f.desde &&
        fecha <= f.hasta &&
        (f.tipoDesastre === null || p.tipoDesastre === f.tipoDesastre) &&
        (f.municipioId === null || esEspecial(f.municipioId) || p.municipioId === f.municipioId)
      );
    })
    .map(({ municipioId: _omitido, ...p }) => p)
    .sort((a, b) => b.fechaRegistro.localeCompare(a.fechaRegistro));
}

export function mockProyectos(filtro?: FiltroIndicadores): EstadoProyectos {
  const proyectos = proyectosFiltrados(normalizar(filtro));
  const finalizados = proyectos.filter((p) => p.estado === 'CERRADA').length;
  const porEstado: CantidadPorEstado[] = ESTADOS.map((estado) => ({
    estado,
    cantidad: proyectos.filter((p) => p.estado === estado).length,
  }));
  return {
    total: proyectos.length,
    finalizados,
    enCurso: proyectos.length - finalizados,
    porcentajeFinalizados: porcentaje(finalizados, proyectos.length),
    porEstado,
    proyectos,
  };
}

export function mockTiemposEtapas(filtro?: FiltroIndicadores): TiemposEtapas {
  const f = normalizar(filtro);
  const casos = proyectosFiltrados(f).filter((p) => p.bonitaCaseId !== null);
  const bonita = estadoBonita(f);
  if (!bonita.disponible || casos.length === 0) {
    return {
      bonita,
      casosAnalizados: casos.length,
      casosFinalizados: 0,
      duracionPromedioTotalHoras: null,
      etapas: etapasMock.map((e) => ({
        ...e,
        muestras: 0,
        promedioHoras: null,
        medianaHoras: null,
        minimoHoras: null,
        maximoHoras: null,
      })),
    };
  }
  // Escala las muestras a la cantidad de casos filtrados (aproximación para el mock).
  const factor = casos.length / proyectosMock.filter((p) => p.bonitaCaseId !== null).length;
  const finalizados = casos.filter((p) => p.estado === 'CERRADA').length;
  return {
    bonita,
    casosAnalizados: casos.length,
    casosFinalizados: finalizados,
    duracionPromedioTotalHoras: finalizados === 0 ? null : 1086.4,
    etapas: etapasMock.map((e) => {
      const muestras = Math.min(casos.length, Math.round(e.muestras * factor));
      return muestras === 0
        ? { ...e, muestras, promedioHoras: null, medianaHoras: null, minimoHoras: null, maximoHoras: null }
        : { ...e, muestras };
    }),
  };
}

export function mockResumen(filtro?: FiltroIndicadores): ResumenIndicadores {
  const f = normalizar(filtro);
  const proyectos = mockProyectos(filtro);
  const tiempos = mockTiemposEtapas(filtro);
  let consorcios: ResolucionConsorcios | null = null;
  try {
    consorcios = mockConsorcios(filtro);
  } catch {
    consorcios = null;
  }
  return {
    generadoEn: new Date().toISOString(),
    filtro: f,
    emergenciasTotales: proyectos.total,
    emergenciasActivas: proyectos.enCurso,
    proyectosFinalizados: proyectos.finalizados,
    porcentajeFinalizados: proyectos.porcentajeFinalizados,
    lotesResueltos: consorcios?.lotesResueltos ?? 0,
    porcentajeResolucionConsorcio: consorcios?.porcentajeConsorcio ?? null,
    tiempoPromedioResolucionHoras: tiempos.duracionPromedioTotalHoras,
    bonita: tiempos.bonita,
  };
}

export function mockReporte(filtro?: FiltroIndicadores): ReporteIndicadores {
  return {
    resumen: mockResumen(filtro),
    suministros: mockSuministros(filtro),
    consorcios: mockConsorcios(filtro),
    proyectos: mockProyectos(filtro),
    tiemposEtapas: mockTiemposEtapas(filtro),
  };
}

/**
 * JSON completo; CSV reducido (resumen y proyectos) con el mismo formato que el backend
 * (separador ';', coma decimal, BOM). Excel no se genera en modo de prueba: para probarlo, usar el backend.
 */
export function mockDescargarReporte(formato: FormatoReporte, filtro?: FiltroIndicadores): Blob {
  const reporte = mockReporte(filtro);
  if (formato === 'json') {
    return new Blob([JSON.stringify(reporte, null, 2)], { type: 'application/json' });
  }
  if (formato === 'csv') {
    const celda = (v: unknown) => (v === null || v === undefined ? '' : String(v).replace('.', ','));
    const r = reporte.resumen;
    const lineas = [
      '# Resumen',
      'Indicador;Valor',
      `Emergencias totales;${celda(r.emergenciasTotales)}`,
      `Emergencias activas;${celda(r.emergenciasActivas)}`,
      `% finalizados;${celda(r.porcentajeFinalizados)}`,
      `% resolución por consorcio;${celda(r.porcentajeResolucionConsorcio)}`,
      '',
      '# Proyectos',
      'Id;Tipo de desastre;Municipio;Estado;% cobertura',
      ...reporte.proyectos.proyectos.map((p) =>
        [p.id, p.tipoDesastre, p.municipio, p.estado, celda(p.porcentajeCobertura)].join(';'),
      ),
    ];
    return new Blob(['﻿' + lineas.join('\r\n') + '\r\n'], { type: 'text/csv;charset=utf-8' });
  }
  throw new ApiError('En modo de prueba (VITE_INDICADORES_MOCK) no se genera Excel.', 501);
}
