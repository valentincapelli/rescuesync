import { useEffect, useState } from 'react';
import { ApiError } from '../api/client';
import { listarVersionesOferta } from '../api/ofertas';
import type { VersionOferta } from '../types/oferta';

function fecha(valor: string): string {
  return new Date(valor).toLocaleString('es-AR', { dateStyle: 'short', timeStyle: 'short' });
}

/** Historial de versiones de una oferta: el alta y cada edición, de la más nueva a la más vieja. */
export function VersionesOferta({ loteId, ofertaId, unidad, recarga }: {
  loteId: number;
  ofertaId: number;
  unidad: string;
  recarga: number;
}) {
  const [versiones, setVersiones] = useState<VersionOferta[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelado = false;
    setError(null);
    listarVersionesOferta(loteId, ofertaId)
      .then((data) => { if (!cancelado) setVersiones(data); })
      .catch((err: unknown) => {
        if (!cancelado) setError(err instanceof ApiError ? err.message : 'No se pudo cargar el historial.');
      });
    return () => { cancelado = true; };
  }, [loteId, ofertaId, recarga]);

  if (error) return <p className="form-banner error" role="alert">{error}</p>;
  if (!versiones) return <p role="status">Cargando historial…</p>;

  return (
    <ol className="versiones-oferta" reversed aria-label={`Versiones de la oferta #${ofertaId}`}>
      {[...versiones].reverse().map((v) => (
        <li key={v.numero}>
          <strong>Versión {v.numero}</strong>{' · '}{fecha(v.fecha)}{' · '}
          {v.autorEmail ? `editada por ${v.autorEmail}` : 'alta de la oferta'}
          <br />
          {v.cantidadOfrecida.toLocaleString('es-AR')} {unidad}
          {v.cantidadAnterior !== null && v.cantidadAnterior !== v.cantidadOfrecida && (
            <span className="muted"> (antes {v.cantidadAnterior.toLocaleString('es-AR')})</span>
          )}
          {v.observaciones && <> · {v.observaciones}</>}
          {v.numero > 1 && (v.observacionesAnteriores ?? '') !== (v.observaciones ?? '') && (
            <span className="muted"> (observaciones antes: {v.observacionesAnteriores || 'sin observaciones'})</span>
          )}
        </li>
      ))}
    </ol>
  );
}
