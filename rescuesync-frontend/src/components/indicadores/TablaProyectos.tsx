import { useMemo, useState } from 'react';
import { ESTADO_EMERGENCIA_LABELS, TIPO_DESASTRE_LABELS, type EstadoEmergencia } from '../../types/emergencia';
import type { ProyectoResumen } from '../../types/indicadores';
import { ESTADOS_EMERGENCIA, formatearFecha, formatearPorcentaje } from './formato';

type Columna = 'id' | 'tipoDesastre' | 'municipio' | 'estado' | 'fechaRegistro' | 'fechaCierre' | 'porcentajeCobertura';
type Direccion = 'asc' | 'desc';

const COLUMNAS: { id: Columna; titulo: string; num?: boolean }[] = [
  { id: 'id', titulo: '#', num: true },
  { id: 'tipoDesastre', titulo: 'Tipo' },
  { id: 'municipio', titulo: 'Municipio' },
  { id: 'estado', titulo: 'Estado' },
  { id: 'fechaRegistro', titulo: 'Registro' },
  { id: 'fechaCierre', titulo: 'Cierre' },
  { id: 'porcentajeCobertura', titulo: '% cobertura', num: true },
];

function valorOrden(p: ProyectoResumen, col: Columna): string | number | null {
  switch (col) {
    case 'id':
      return p.id;
    case 'tipoDesastre':
      return TIPO_DESASTRE_LABELS[p.tipoDesastre];
    case 'municipio':
      return p.municipio;
    case 'estado':
      // Orden del flujo, no alfabético.
      return ESTADOS_EMERGENCIA.indexOf(p.estado);
    case 'fechaRegistro':
      return p.fechaRegistro;
    case 'fechaCierre':
      return p.fechaCierre;
    case 'porcentajeCobertura':
      return p.porcentajeCobertura;
  }
}

function comparar(a: ProyectoResumen, b: ProyectoResumen, col: Columna, dir: Direccion): number {
  const va = valorOrden(a, col);
  const vb = valorOrden(b, col);
  // Los vacíos van siempre al final, en cualquier dirección.
  if (va === null && vb === null) return b.id - a.id;
  if (va === null) return 1;
  if (vb === null) return -1;
  const base = typeof va === 'number' && typeof vb === 'number' ? va - vb : String(va).localeCompare(String(vb), 'es');
  return (dir === 'asc' ? base : -base) || b.id - a.id;
}

interface Props {
  proyectos: ProyectoResumen[];
  filtroEstado: EstadoEmergencia | null;
  onQuitarFiltro: () => void;
}

/** Tabla de proyectos (emergencias del período), ordenable por columna. */
export function TablaProyectos({ proyectos, filtroEstado, onQuitarFiltro }: Props) {
  const [orden, setOrden] = useState<{ col: Columna; dir: Direccion }>({ col: 'fechaRegistro', dir: 'desc' });

  const visibles = useMemo(() => {
    const filtrados = filtroEstado ? proyectos.filter((p) => p.estado === filtroEstado) : proyectos;
    return [...filtrados].sort((a, b) => comparar(a, b, orden.col, orden.dir));
  }, [proyectos, filtroEstado, orden]);

  function ordenarPor(col: Columna) {
    setOrden((prev) =>
      prev.col === col
        ? { col, dir: prev.dir === 'asc' ? 'desc' : 'asc' }
        : { col, dir: col === 'fechaRegistro' || col === 'fechaCierre' || col === 'porcentajeCobertura' ? 'desc' : 'asc' },
    );
  }

  return (
    <div>
      {filtroEstado && (
        <div className="ind-filtro-activo" role="status">
          Mostrando solo proyectos en estado <strong>{ESTADO_EMERGENCIA_LABELS[filtroEstado]}</strong> ({visibles.length} de{' '}
          {proyectos.length}).
          <button type="button" className="link" onClick={onQuitarFiltro}>
            Quitar filtro ×
          </button>
        </div>
      )}
      {visibles.length === 0 ? (
        <p className="ind-estado muted">No hay proyectos para mostrar.</p>
      ) : (
        <div className="table-wrap">
          <table className="lotes-table ind-tabla">
            <thead>
              <tr>
                {COLUMNAS.map((c) => {
                  const activa = orden.col === c.id;
                  return (
                    <th
                      key={c.id}
                      scope="col"
                      className={c.num ? 'num' : undefined}
                      aria-sort={activa ? (orden.dir === 'asc' ? 'ascending' : 'descending') : 'none'}
                    >
                      <button type="button" className="ind-orden" onClick={() => ordenarPor(c.id)}>
                        {c.titulo}
                        <span aria-hidden="true" className={activa ? 'ind-flecha activa' : 'ind-flecha'}>
                          {activa ? (orden.dir === 'asc' ? '▲' : '▼') : '↕'}
                        </span>
                      </button>
                    </th>
                  );
                })}
              </tr>
            </thead>
            <tbody>
              {visibles.map((p) => (
                <tr key={p.id}>
                  <td className="num">{p.id}</td>
                  <td>
                    {TIPO_DESASTRE_LABELS[p.tipoDesastre]}
                    <div className="muted ind-sub">{p.zonaAfectada}</div>
                  </td>
                  <td>{p.municipio}</td>
                  <td>
                    <span className={`badge ind-estado-${p.estado.toLowerCase()}`}>{ESTADO_EMERGENCIA_LABELS[p.estado]}</span>
                  </td>
                  <td>{formatearFecha(p.fechaRegistro)}</td>
                  <td>{formatearFecha(p.fechaCierre)}</td>
                  <td className="num">
                    {p.porcentajeCobertura === null ? (
                      <span className="muted" title="La emergencia no tiene lotes">
                        Sin datos
                      </span>
                    ) : (
                      <span className="ind-cobertura">
                        <span className="ind-cobertura-barra" aria-hidden="true">
                          <span style={{ width: `${Math.min(100, Math.max(0, p.porcentajeCobertura))}%` }} />
                        </span>
                        {formatearPorcentaje(p.porcentajeCobertura)}
                      </span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
