import type { ReactNode } from 'react';
import type { EstadoBloque } from './useBloque';

interface Props<T> {
  titulo: string;
  descripcion?: ReactNode;
  estado: EstadoBloque<T>;
  onReintentar: () => void;
  /** Si devuelve true con los datos cargados, se muestra el mensaje de vacío. */
  estaVacio?: (datos: T) => boolean;
  mensajeVacio?: string;
  acciones?: ReactNode;
  className?: string;
  children: (datos: T) => ReactNode;
}

/** Tarjeta de un bloque del dashboard con sus estados de carga, error (con reintento) y vacío. */
export function BloqueIndicador<T>({
  titulo,
  descripcion,
  estado,
  onReintentar,
  estaVacio,
  mensajeVacio = 'No hay datos para los filtros elegidos.',
  acciones,
  className,
  children,
}: Props<T>) {
  const datos = estado.tipo === 'error' ? null : estado.datos;
  const cargando = estado.tipo === 'cargando';

  let contenido: ReactNode;
  if (estado.tipo === 'error') {
    contenido = (
      <div className="ind-estado ind-estado-error" role="alert">
        <p>{estado.mensaje}</p>
        <button type="button" className="secondary" onClick={onReintentar}>
          Reintentar
        </button>
      </div>
    );
  } else if (datos === null) {
    contenido = (
      <div className="ind-estado" aria-live="polite">
        <span className="ind-spinner" aria-hidden="true" /> Cargando…
      </div>
    );
  } else if (estaVacio?.(datos)) {
    contenido = <p className="ind-estado muted">{mensajeVacio}</p>;
  } else {
    contenido = children(datos);
  }

  return (
    <section className={`ind-bloque${className ? ` ${className}` : ''}`} aria-busy={cargando}>
      <header className="ind-bloque-header">
        <div>
          <h3>{titulo}</h3>
          {descripcion && <p className="muted">{descripcion}</p>}
        </div>
        <div className="ind-bloque-acciones">
          {cargando && datos !== null && <span className="ind-spinner" title="Actualizando…" />}
          {acciones}
        </div>
      </header>
      <div className={cargando && datos !== null ? 'ind-bloque-body actualizando' : 'ind-bloque-body'}>{contenido}</div>
    </section>
  );
}
