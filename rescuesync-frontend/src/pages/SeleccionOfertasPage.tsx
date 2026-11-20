import { useEffect, useState } from 'react';
import { ApiError } from '../api/client';
import { obtenerOfertasEvaluadas, seleccionarOfertas } from '../api/ofertas';
import {
  ESTADO_EMERGENCIA_LABELS,
  NIVEL_GRAVEDAD_LABELS,
  TIPO_DESASTRE_LABELS,
} from '../types/emergencia';
import { TIPO_RECURSO_LABELS } from '../types/lote';
import {
  CLASIFICACION_OFERTA_LABELS,
  NIVEL_COMPETENCIA_LABELS,
  type EmergenciaConOfertasEvaluadas,
  type ValidacionOferta,
} from '../types/oferta';
import './SeleccionOfertasPage.css';

function mensajeDeError(error: unknown): string {
  return error instanceof ApiError
    ? error.message
    : 'No se pudieron cargar las ofertas evaluadas.';
}

function formatearFecha(iso: string): string {
  const [anio, mes, dia] = iso.split('-');
  return `${dia}/${mes}/${anio}`;
}

/** Perfil del Sistema Nacional y cómo se adapta la oferta al lote (principal o apoyo). */
function PerfilNacional({ validacion }: { validacion: ValidacionOferta | null }) {
  if (!validacion) {
    return <span className="muted">Sin datos de validación</span>;
  }
  return (
    <div className="perfil-nacional">
      <span className={`badge clasificacion-${validacion.clasificacion.toLowerCase()}`}>
        {CLASIFICACION_OFERTA_LABELS[validacion.clasificacion]}
      </span>
      {validacion.nivelCompetencia && validacion.nivelRiesgoMax && (
        <span className="muted">
          Competencia {NIVEL_COMPETENCIA_LABELS[validacion.nivelCompetencia]} · riesgo hasta{' '}
          {NIVEL_GRAVEDAD_LABELS[validacion.nivelRiesgoMax]}
          {validacion.certificacionVence &&
            ` · vence ${formatearFecha(validacion.certificacionVence)}`}
        </span>
      )}
    </div>
  );
}

export function SeleccionOfertasPage() {
  const [emergencias, setEmergencias] = useState<EmergenciaConOfertasEvaluadas[]>([]);
  const [emergenciaId, setEmergenciaId] = useState<number | null>(null);
  const [ofertasSeleccionadas, setOfertasSeleccionadas] = useState<Set<number>>(new Set());
  const [cargando, setCargando] = useState(true);
  const [guardando, setGuardando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [resultado, setResultado] = useState<string | null>(null);
  const [recarga, setRecarga] = useState(0);

  const emergencia = emergencias.find((item) => item.id === emergenciaId) ?? null;

  useEffect(() => {
    let cancelado = false;
    setCargando(true);
    setError(null);

    obtenerOfertasEvaluadas()
      .then((data) => {
        if (cancelado) return;
        setEmergencias(data);
        setEmergenciaId((actual) =>
          data.some((item) => item.id === actual) ? actual : (data[0]?.id ?? null),
        );
        setOfertasSeleccionadas(new Set());
      })
      .catch((err: unknown) => {
        if (cancelado) return;
        setEmergencias([]);
        setEmergenciaId(null);
        setError(mensajeDeError(err));
      })
      .finally(() => !cancelado && setCargando(false));

    return () => {
      cancelado = true;
    };
  }, [recarga]);

  function cambiarEmergencia(id: number | null) {
    setEmergenciaId(id);
    setOfertasSeleccionadas(new Set());
    setResultado(null);
  }

  function alternarOferta(ofertaId: number, marcada: boolean) {
    setOfertasSeleccionadas((actuales) => {
      const nuevas = new Set(actuales);
      if (marcada) {
        nuevas.add(ofertaId);
      } else {
        nuevas.delete(ofertaId);
      }
      return nuevas;
    });
    setResultado(null);
  }

  async function confirmarSeleccion() {
    if (!emergencia || ofertasSeleccionadas.size === 0) return;

    setGuardando(true);
    setError(null);
    setResultado(null);
    try {
      const respuesta = await seleccionarOfertas(
        emergencia.id,
        Array.from(ofertasSeleccionadas),
      );
      setResultado(
        `Selección confirmada: ${respuesta.cantidadSeleccionadas} ofertas aceptadas y `
        + `${respuesta.cantidadRechazadas} rechazadas.`,
      );
      setOfertasSeleccionadas(new Set());
      setRecarga((actual) => actual + 1);
    } catch (err) {
      setError(mensajeDeError(err));
    } finally {
      setGuardando(false);
    }
  }

  return (
    <section className="seleccion-ofertas-page">
      <h2>Seleccionar ofertas para una emergencia</h2>
      <p>
        Revisá las ofertas validadas por el Sistema Nacional y elegí cuáles aceptar. Una oferta es{' '}
        <strong>principal</strong> si su riesgo máximo certificado alcanza la gravedad de la emergencia,
        y de <strong>apoyo secundario</strong> si es menor.
      </p>

      <div className="emergencia-picker">
        <div className="form-field">
          <label htmlFor="emergenciaSeleccion">Emergencia con ofertas validadas</label>
          <select
            id="emergenciaSeleccion"
            value={emergenciaId ?? ''}
            disabled={cargando || guardando || emergencias.length === 0}
            onChange={(event) =>
              cambiarEmergencia(event.target.value ? Number(event.target.value) : null)
            }
          >
            <option value="">
              {cargando ? 'Cargando…' : 'Elegí una emergencia…'}
            </option>
            {emergencias.map((item) => (
              <option key={item.id} value={item.id}>
                #{item.id} · {TIPO_DESASTRE_LABELS[item.tipoDesastre]} — {item.zonaAfectada}
              </option>
            ))}
          </select>
        </div>
        <button
          type="button"
          className="secondary"
          disabled={cargando || guardando}
          onClick={() => {
            setResultado(null);
            setRecarga((actual) => actual + 1);
          }}
        >
          Actualizar
        </button>
      </div>

      {error && <p className="form-banner error" role="alert">{error}</p>}
      {resultado && <p className="form-banner info" role="status">{resultado}</p>}
      {cargando && <p className="muted" role="status">Cargando emergencias y ofertas…</p>}
      {!cargando && !error && emergencias.length === 0 && (
        <p className="form-banner info">
          No hay emergencias en cobertura evaluada o parcial con ofertas validadas para seleccionar.
        </p>
      )}

      {!cargando && emergencia && (
        <>
          <div className="emergencia-resumen">
            <strong>
              Emergencia #{emergencia.id} · {TIPO_DESASTRE_LABELS[emergencia.tipoDesastre]}
            </strong>{' '}
            <span className={`badge gravedad-${emergencia.nivelGravedad.toLowerCase()}`}>
              Gravedad {NIVEL_GRAVEDAD_LABELS[emergencia.nivelGravedad]}
            </span>{' '}
            <span className="badge">
              {ESTADO_EMERGENCIA_LABELS[emergencia.estado]}
            </span>
            <div className="muted">{emergencia.municipio} — {emergencia.zonaAfectada}</div>
            <p>{emergencia.descripcion}</p>
          </div>

          <div className="lotes-seleccion">
            {emergencia.lotes.map((lote) => (
              <fieldset className="lote-seleccion" key={lote.id}>
                <legend>
                  Lote #{lote.id} · {TIPO_RECURSO_LABELS[lote.tipoRecurso]}
                </legend>
                <p>{lote.descripcion}</p>
                <p className="muted">
                  Requerido: {lote.cantidadRequerida.toLocaleString('es-AR')} {lote.unidadMedida}
                </p>
                <div className="table-wrap">
                  <table className="lotes-table">
                    <thead>
                      <tr>
                        <th scope="col">Aceptar</th>
                        <th scope="col">ONG</th>
                        <th scope="col" className="num">Cantidad ofrecida</th>
                        <th scope="col">Perfil nacional</th>
                        <th scope="col">Observaciones</th>
                      </tr>
                    </thead>
                    <tbody>
                      {lote.ofertas.map((oferta) => (
                        <tr key={oferta.id}>
                          <td>
                            <input
                              type="checkbox"
                              aria-label={`Aceptar oferta de ${oferta.ongNombre} para lote ${lote.id}`}
                              checked={ofertasSeleccionadas.has(oferta.id)}
                              disabled={guardando}
                              onChange={(event) =>
                                alternarOferta(oferta.id, event.target.checked)
                              }
                            />
                          </td>
                          <td>{oferta.ongNombre}</td>
                          <td className="num">
                            {oferta.cantidadOfrecida.toLocaleString('es-AR')} {lote.unidadMedida}
                          </td>
                          <td><PerfilNacional validacion={oferta.validacion} /></td>
                          <td>{oferta.observaciones || '—'}</td>
                        </tr>
                      ))}
                      {lote.ofertas.length === 0 && (
                        <tr>
                          <td colSpan={5} className="muted">
                            Este lote no tiene ofertas validadas para seleccionar.
                          </td>
                        </tr>
                      )}
                    </tbody>
                  </table>
                </div>
                {lote.ofertasSinHabilitacion.length > 0 && (
                  <details className="sin-habilitacion">
                    <summary>
                      {lote.ofertasSinHabilitacion.length === 1
                        ? '1 oferta sin habilitación nacional'
                        : `${lote.ofertasSinHabilitacion.length} ofertas sin habilitación nacional`}{' '}
                      (no se pueden seleccionar)
                    </summary>
                    <ul>
                      {lote.ofertasSinHabilitacion.map((oferta) => (
                        <li key={oferta.id}>
                          <strong>{oferta.ongNombre}</strong> ·{' '}
                          {oferta.cantidadOfrecida.toLocaleString('es-AR')} {lote.unidadMedida}
                          {oferta.validacion?.observaciones && (
                            <span className="muted"> — {oferta.validacion.observaciones}</span>
                          )}
                        </li>
                      ))}
                    </ul>
                  </details>
                )}
              </fieldset>
            ))}
          </div>

          <div className="confirmar-seleccion">
            <p>
              Seleccionadas: <strong>{ofertasSeleccionadas.size}</strong>. Las demás ofertas
              evaluadas de esta emergencia se rechazarán al confirmar.
            </p>
            <button
              type="button"
              disabled={guardando || ofertasSeleccionadas.size === 0}
              onClick={() => void confirmarSeleccion()}
            >
              {guardando ? 'Confirmando…' : 'Confirmar selección'}
            </button>
          </div>
        </>
      )}
    </section>
  );
}
