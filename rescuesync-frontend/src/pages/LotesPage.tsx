import { useCallback, useEffect, useState } from 'react';
import './AltaEmergenciaPage.css';
import './LotesPage.css';
import { ApiError } from '../api/client';
import { listarEmergencias, publicarConvocatoria } from '../api/emergencias';
import { crearLote, editarLote, eliminarLote, listarLotes } from '../api/lotes';
import { LoteForm } from '../components/LoteForm';
import {
  ESTADO_EMERGENCIA_LABELS,
  ESTADOS_DESGLOSE_ABIERTO,
  NIVEL_GRAVEDAD_LABELS,
  TIPO_DESASTRE_LABELS,
  type Emergencia,
} from '../types/emergencia';
import { ESTADO_LOTE_LABELS, TIPO_RECURSO_LABELS, type Lote, type LoteRequest } from '../types/lote';

function mensajeDeError(err: unknown, fallback: string): string {
  return err instanceof ApiError ? err.message : fallback;
}

/**
 * Tarea "Revisar y desglosar emergencia" + "Publicar convocatoria" del Centro Coordinador
 * Regional (E2-07). Elige una emergencia, la desglosa en lotes (BORRADOR) y publica la
 * convocatoria, a partir de la cual las ONGs pueden ofertar.
 */
export function LotesPage() {
  const [emergencias, setEmergencias] = useState<Emergencia[]>([]);
  const [cargandoEmergencias, setCargandoEmergencias] = useState(true);
  const [seleccionadaId, setSeleccionadaId] = useState<number | null>(null);

  const [lotes, setLotes] = useState<Lote[]>([]);
  const [cargandoLotes, setCargandoLotes] = useState(false);
  const [editando, setEditando] = useState<Lote | null>(null);
  const [publicando, setPublicando] = useState(false);

  const [error, setError] = useState<string | null>(null);
  const [aviso, setAviso] = useState<string | null>(null);

  const seleccionada = emergencias.find((e) => e.id === seleccionadaId) ?? null;
  const desgloseAbierto = seleccionada !== null && ESTADOS_DESGLOSE_ABIERTO.includes(seleccionada.estado);

  const cargarEmergencias = useCallback(async () => {
    setCargandoEmergencias(true);
    try {
      const data = await listarEmergencias();
      // Primero las que el CCR todavía tiene que desglosar, después las más nuevas.
      data.sort((a, b) => {
        const abiertaA = ESTADOS_DESGLOSE_ABIERTO.includes(a.estado) ? 0 : 1;
        const abiertaB = ESTADOS_DESGLOSE_ABIERTO.includes(b.estado) ? 0 : 1;
        return abiertaA - abiertaB || b.id - a.id;
      });
      setEmergencias(data);
    } catch (err) {
      setError(mensajeDeError(err, 'No se pudieron cargar las emergencias.'));
    } finally {
      setCargandoEmergencias(false);
    }
  }, []);

  useEffect(() => {
    void cargarEmergencias();
  }, [cargarEmergencias]);

  useEffect(() => {
    setEditando(null);
    setAviso(null);
    setError(null);
    if (seleccionadaId === null) {
      setLotes([]);
      return;
    }
    let cancelado = false;
    setCargandoLotes(true);
    listarLotes(seleccionadaId)
      .then((data) => !cancelado && setLotes(data))
      .catch((err: unknown) => !cancelado && setError(mensajeDeError(err, 'No se pudieron cargar los lotes.')))
      .finally(() => !cancelado && setCargandoLotes(false));
    return () => {
      cancelado = true;
    };
  }, [seleccionadaId]);

  // Refleja en la lista un cambio de estado de la emergencia hecho por el backend.
  function actualizarEstado(id: number, estado: Emergencia['estado']) {
    setEmergencias((prev) => prev.map((e) => (e.id === id ? { ...e, estado } : e)));
  }

  async function guardarLote(request: LoteRequest) {
    if (!seleccionada) return;
    setAviso(null);
    setError(null);
    if (editando) {
      const actualizado = await editarLote(seleccionada.id, editando.id, request);
      setLotes((prev) => prev.map((l) => (l.id === actualizado.id ? actualizado : l)));
      setEditando(null);
      setAviso(`Lote #${actualizado.id} actualizado.`);
    } else {
      const creado = await crearLote(seleccionada.id, request);
      setLotes((prev) => [...prev, creado]);
      // El backend pasa la emergencia a EN_REVISION al cargar el primer lote.
      if (seleccionada.estado === 'REGISTRADA') {
        actualizarEstado(seleccionada.id, 'EN_REVISION');
      }
      setAviso(`Lote #${creado.id} agregado.`);
    }
  }

  async function borrarLote(lote: Lote) {
    if (!seleccionada) return;
    if (!window.confirm(`¿Borrar el lote #${lote.id} (${lote.cantidadRequerida} ${lote.unidadMedida})?`)) return;
    setAviso(null);
    setError(null);
    try {
      await eliminarLote(seleccionada.id, lote.id);
      setLotes((prev) => prev.filter((l) => l.id !== lote.id));
      if (editando?.id === lote.id) setEditando(null);
      setAviso(`Lote #${lote.id} borrado.`);
    } catch (err) {
      setError(mensajeDeError(err, 'No se pudo borrar el lote.'));
    }
  }

  async function publicar() {
    if (!seleccionada) return;
    const confirmado = window.confirm(
      `Vas a publicar la convocatoria de la emergencia #${seleccionada.id} con ${lotes.length} lote(s).\n` +
        'Después ya no se van a poder agregar, editar ni borrar lotes. ¿Continuar?',
    );
    if (!confirmado) return;

    setPublicando(true);
    setAviso(null);
    setError(null);
    try {
      const emergencia = await publicarConvocatoria(seleccionada.id);
      actualizarEstado(emergencia.id, emergencia.estado);
      setLotes(await listarLotes(emergencia.id));
      setEditando(null);
      setAviso('Convocatoria publicada: las ONGs ya pueden cargar ofertas sobre estos lotes.');
    } catch (err) {
      setError(mensajeDeError(err, 'No se pudo publicar la convocatoria.'));
    } finally {
      setPublicando(false);
    }
  }

  return (
    <section>
      <h2>Desglosar emergencia en lotes</h2>
      <p>
        El Centro Coordinador Regional revisa una emergencia registrada, la desglosa en lotes de
        necesidades (ej: 5 paramédicos, 1000 raciones) y publica la convocatoria para que las ONGs
        puedan ofertar.
      </p>

      <div className="emergencia-picker">
        <div className="form-field">
          <label htmlFor="emergencia">Emergencia</label>
          <select
            id="emergencia"
            value={seleccionadaId ?? ''}
            onChange={(e) => setSeleccionadaId(e.target.value ? Number(e.target.value) : null)}
            disabled={cargandoEmergencias}
          >
            <option value="">
              {cargandoEmergencias
                ? 'Cargando…'
                : emergencias.length === 0
                  ? 'No hay emergencias registradas'
                  : 'Elegí una emergencia…'}
            </option>
            {emergencias.map((e) => (
              <option key={e.id} value={e.id}>
                #{e.id} · {TIPO_DESASTRE_LABELS[e.tipoDesastre]} en {e.zonaAfectada} ({e.municipio}) —{' '}
                {ESTADO_EMERGENCIA_LABELS[e.estado]}
              </option>
            ))}
          </select>
        </div>
        <button type="button" className="secondary" onClick={() => void cargarEmergencias()} disabled={cargandoEmergencias}>
          Actualizar
        </button>
      </div>

      {error && <p className="form-banner error">{error}</p>}
      {aviso && <p className="form-banner success">{aviso}</p>}

      {seleccionada && (
        <>
          <div className="emergencia-resumen">
            <div>
              <strong>
                #{seleccionada.id} · {TIPO_DESASTRE_LABELS[seleccionada.tipoDesastre]}
              </strong>{' '}
              <span className={`badge gravedad-${seleccionada.nivelGravedad.toLowerCase()}`}>
                Gravedad {NIVEL_GRAVEDAD_LABELS[seleccionada.nivelGravedad]}
              </span>{' '}
              <span className="badge">{ESTADO_EMERGENCIA_LABELS[seleccionada.estado]}</span>
            </div>
            <div className="muted">
              {seleccionada.municipio} — {seleccionada.zonaAfectada}
            </div>
            <p>{seleccionada.descripcion}</p>
          </div>

          <h3>Lotes ({lotes.length})</h3>
          {cargandoLotes ? (
            <p className="muted">Cargando lotes…</p>
          ) : lotes.length === 0 ? (
            <p className="muted">Todavía no hay lotes cargados para esta emergencia.</p>
          ) : (
            <div className="table-wrap">
              <table className="lotes-table">
                <thead>
                  <tr>
                    <th>#</th>
                    <th>Recurso</th>
                    <th>Descripción</th>
                    <th className="num">Cantidad</th>
                    <th>Estado</th>
                    {desgloseAbierto && <th aria-label="Acciones" />}
                  </tr>
                </thead>
                <tbody>
                  {lotes.map((lote) => (
                    <tr key={lote.id} className={editando?.id === lote.id ? 'editing' : undefined}>
                      <td>{lote.id}</td>
                      <td>{TIPO_RECURSO_LABELS[lote.tipoRecurso]}</td>
                      <td>{lote.descripcion}</td>
                      <td className="num">
                        {lote.cantidadRequerida.toLocaleString('es-AR')} {lote.unidadMedida}
                      </td>
                      <td>
                        <span className={`badge estado-${lote.estado.toLowerCase()}`}>{ESTADO_LOTE_LABELS[lote.estado]}</span>
                      </td>
                      {desgloseAbierto && (
                        <td className="acciones">
                          <button type="button" className="link" onClick={() => setEditando(lote)}>
                            Editar
                          </button>
                          <button type="button" className="link danger" onClick={() => void borrarLote(lote)}>
                            Borrar
                          </button>
                        </td>
                      )}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {desgloseAbierto ? (
            <>
              <LoteForm editando={editando} onSubmit={guardarLote} onCancelEdit={() => setEditando(null)} />

              <div className="publicar-box">
                <div>
                  <strong>Publicar convocatoria</strong>
                  <p className="muted">
                    Abre la ventana de ofertas para las ONGs. Después de publicar, los lotes no se pueden modificar.
                  </p>
                </div>
                <div className="form-actions">
                  <button
                    type="button"
                    onClick={() => void publicar()}
                    disabled={publicando || lotes.length === 0 || editando !== null}
                    title={lotes.length === 0 ? 'Cargá al menos un lote' : undefined}
                  >
                    {publicando ? 'Publicando…' : 'Publicar convocatoria'}
                  </button>
                </div>
              </div>
            </>
          ) : (
            <p className="form-banner info">
              La convocatoria de esta emergencia ya fue publicada ({ESTADO_EMERGENCIA_LABELS[seleccionada.estado]}): los
              lotes son de solo lectura.
            </p>
          )}
        </>
      )}
    </section>
  );
}
