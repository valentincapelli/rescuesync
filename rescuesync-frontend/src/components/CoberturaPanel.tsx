import { useEffect, useState } from 'react';
import { ApiError } from '../api/client';
import { consultarCobertura, decidirCobertura, resolverCobertura, sincronizarCobertura, type Cobertura } from '../api/cobertura';
import { publicarConvocatoria } from '../api/emergencias';
import type { Emergencia } from '../types/emergencia';
import { TIPO_RECURSO_LABELS } from '../types/lote';

interface Props {
  emergenciaId: number;
  onActualizada: (emergencia: Emergencia) => void;
}

export function CoberturaPanel({ emergenciaId, onActualizada }: Props) {
  const [cobertura, setCobertura] = useState<Cobertura | null>(null);
  const [cargando, setCargando] = useState(true);
  const [guardando, setGuardando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [aviso, setAviso] = useState<string | null>(null);
  const [resolucion, setResolucion] = useState('');

  useEffect(() => {
    let cancelado = false;
    consultarCobertura(emergenciaId)
      .then((data) => !cancelado && setCobertura(data))
      .catch((err: unknown) => !cancelado && setError(mensaje(err)))
      .finally(() => !cancelado && setCargando(false));
    return () => { cancelado = true; };
  }, [emergenciaId]);

  function mensaje(err: unknown) {
    return err instanceof ApiError ? err.message : 'No se pudo actualizar la cobertura.';
  }

  async function ejecutar(accion: () => Promise<unknown>, exito: string) {
    if (guardando) return;
    setGuardando(true);
    setError(null);
    setAviso(null);
    try {
      await accion();
      const actualizada = await consultarCobertura(emergenciaId);
      setCobertura(actualizada);
      onActualizada(actualizada.emergencia);
      if (!actualizada.sincronizacionPendiente) setAviso(exito);
    } catch (err: unknown) {
      setError(mensaje(err));
    } finally {
      setGuardando(false);
    }
  }

  const completa = !!cobertura?.lotes.length && cobertura.lotes.every((l) => l.totalOfrecido >= l.cantidadRequerida);
  const hayOfertas = cobertura?.lotes.some((l) => l.totalOfrecido > 0);
  const puedeEvaluar = cobertura?.emergencia.estado === 'CONVOCATORIA_PUBLICADA'
    && cobertura.plazoVencido && !cobertura.republicacionPendiente && !cobertura.sincronizacionPendiente;

  return (
    <section className="cobertura-panel" aria-labelledby="cobertura-titulo">
      <div className="form-actions">
        <h3 id="cobertura-titulo">Cobertura de la convocatoria</h3>
        <button type="button" className="secondary" disabled={cargando || guardando}
          onClick={() => void ejecutar(() => sincronizarCobertura(emergenciaId), 'Cobertura actualizada.')}>
          {guardando ? 'Actualizando…' : 'Actualizar cobertura'}
        </button>
      </div>
      {error && <p className="form-banner error" role="alert">{error}</p>}
      {aviso && <p className="form-banner success" role="status">{aviso}</p>}
      {cargando ? <p role="status">Cargando cobertura…</p> : cobertura && (
        <>
          {cobertura.fechaCierreConvocatoria && <p className="muted">Cierre de convocatoria:{' '}
            {new Intl.DateTimeFormat('es-AR', { dateStyle: 'medium', timeStyle: 'short' })
              .format(new Date(cobertura.fechaCierreConvocatoria))}.</p>}
          <div className="table-wrap">
            <table className="lotes-table">
              <caption>Cantidades recibidas por lote</caption>
              <thead><tr><th>Lote</th><th>Requerido</th><th>Ofrecido</th><th>Cobertura</th></tr></thead>
              <tbody>{cobertura.lotes.map((l) => <tr key={l.id}>
                <td>#{l.id} · {TIPO_RECURSO_LABELS[l.tipoRecurso]}<br /><span className="muted">{l.descripcion}</span></td>
                <td className="num">{l.cantidadRequerida.toLocaleString('es-AR')} {l.unidadMedida}</td>
                <td className="num">{l.totalOfrecido.toLocaleString('es-AR')} {l.unidadMedida}</td>
                <td>{l.totalOfrecido >= l.cantidadRequerida ? 'Completa' : l.totalOfrecido > 0 ? 'Parcial' : 'Sin ofertas'}</td>
              </tr>)}</tbody>
            </table>
          </div>
          {cobertura.sincronizacionPendiente ? (
            <p className="form-banner info" role="status">La operación está registrada y espera confirmación del proceso.
              Usá “Actualizar cobertura” para consultar su resultado.</p>
          ) : cobertura.republicacionPendiente ? (
            <div className="form-actions">
              <p>La evaluación indicó que hace falta una nueva convocatoria.</p>
              <button type="button" disabled={guardando}
                onClick={() => void ejecutar(() => publicarConvocatoria(emergenciaId), 'Convocatoria reabierta con un nuevo plazo.')}>
                Publicar nuevamente</button>
            </div>
          ) : cobertura.resolucionPendiente ? (
            <form className="emergencia-form" onSubmit={(event) => {
              event.preventDefault();
              if (resolucion.trim()) void ejecutar(() => resolverCobertura(emergenciaId, resolucion.trim()), 'Resolución registrada. Continúa la evaluación de ofertas.');
            }}>
              <div className="form-field">
                <label htmlFor="resolucion-cobertura">Resolución de la cobertura incompleta</label>
                <textarea id="resolucion-cobertura" required maxLength={2000} disabled={guardando}
                  value={resolucion} onChange={(e) => setResolucion(e.target.value)}
                  placeholder="Indicá cómo se atenderá la emergencia con los recursos disponibles." />
              </div>
              <div className="form-actions"><button type="submit" disabled={guardando || !resolucion.trim()}>
                Continuar con cobertura parcial</button></div>
            </form>
          ) : puedeEvaluar ? (
            <>
              <div className="form-actions">
                <button type="button" disabled={guardando || !completa}
                  onClick={() => void ejecutar(() => decidirCobertura(emergenciaId, 'COMPLETA'), 'Cobertura completa confirmada.')}>
                  Confirmar cobertura completa</button>
                <button type="button" className="secondary" disabled={guardando || completa || !hayOfertas}
                  title="Seguir adelante con las ofertas recibidas, aunque algún lote quede sin cubrir."
                  onClick={() => void ejecutar(() => decidirCobertura(emergenciaId, 'PARCIAL'), 'Cobertura parcial registrada. Indicá la resolución.')}>
                  Resolver cobertura parcial</button>
                <button type="button" className="secondary" disabled={guardando || completa || cobertura.lotes.length === 0}
                  title="Reabrir la convocatoria con un nuevo plazo para recibir más ofertas. Las ofertas ya recibidas se conservan."
                  onClick={() => void ejecutar(() => decidirCobertura(emergenciaId, 'SIN_COBERTURA'), 'Nueva convocatoria pendiente de publicación.')}>
                  Solicitar nueva convocatoria</button>
              </div>
              <p className="muted">
                <strong>Resolver cobertura parcial</strong> sigue con las ofertas recibidas (requiere al menos una).{' '}
                <strong>Solicitar nueva convocatoria</strong> reabre el plazo para conseguir más ofertas; las ya recibidas
                se conservan y está disponible con o sin ofertas mientras la cobertura no sea completa.
              </p>
            </>
          ) : cobertura.emergencia.estado === 'CONVOCATORIA_PUBLICADA' && !cobertura.plazoVencido ? (
            <p className="form-banner info">La evaluación estará disponible al vencer la convocatoria. Actualizá la cobertura cuando finalice el plazo.</p>
          ) : <p className="form-banner info">La evaluación de cobertura ya fue registrada.</p>}
          {cobertura.resolucion && !cobertura.resolucionPendiente && <p><strong>Resolución:</strong> {cobertura.resolucion}</p>}
        </>
      )}
    </section>
  );
}
