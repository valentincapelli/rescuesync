import { useEffect, useState } from 'react';
import './AltaEmergenciaPage.css';
import './LotesPage.css';
import './OfertasPage.css';
import { ApiError } from '../api/client';
import { listarEmergencias } from '../api/emergencias';
import { listarLotes } from '../api/lotes';
import { crearOferta } from '../api/ofertas';
import { OfertaForm } from '../components/OfertaForm';
import { NIVEL_GRAVEDAD_LABELS, TIPO_DESASTRE_LABELS, type Emergencia } from '../types/emergencia';
import { TIPO_RECURSO_LABELS, type Lote } from '../types/lote';
import type { CrearOfertaRequest } from '../types/oferta';

function mensajeDeError(err: unknown, fallback: string): string {
  return err instanceof ApiError ? err.message : fallback;
}

/** Carga básica de ofertas de ONGs sobre los lotes de una convocatoria publicada (E2-09). */
export function OfertasPage() {
  const [emergencias, setEmergencias] = useState<Emergencia[]>([]);
  const [emergenciaId, setEmergenciaId] = useState<number | null>(null);
  const [lotes, setLotes] = useState<Lote[]>([]);
  const [loteId, setLoteId] = useState<number | null>(null);
  const [cargandoEmergencias, setCargandoEmergencias] = useState(true);
  const [cargandoLotes, setCargandoLotes] = useState(false);
  const [guardando, setGuardando] = useState(false);
  const [errorEmergencias, setErrorEmergencias] = useState<string | null>(null);
  const [errorLotes, setErrorLotes] = useState<string | null>(null);
  const [recarga, setRecarga] = useState(0);

  const emergencia = emergencias.find((e) => e.id === emergenciaId) ?? null;
  const lote = lotes.find((l) => l.id === loteId) ?? null;

  useEffect(() => {
    let cancelado = false;
    setCargandoEmergencias(true);
    setErrorEmergencias(null);
    listarEmergencias()
      .then((data) => {
        if (cancelado) return;
        const publicadas = data.filter((e) => e.estado === 'CONVOCATORIA_PUBLICADA').sort((a, b) => b.id - a.id);
        setEmergencias(publicadas);
        setEmergenciaId((id) => publicadas.some((e) => e.id === id) ? id : null);
      })
      .catch((err: unknown) => {
        if (cancelado) return;
        setEmergencias([]);
        setEmergenciaId(null);
        setErrorEmergencias(mensajeDeError(err, 'No se pudieron cargar las convocatorias.'));
      })
      .finally(() => !cancelado && setCargandoEmergencias(false));
    return () => { cancelado = true; };
  }, [recarga]);

  useEffect(() => {
    let cancelado = false;
    setLotes([]);
    setLoteId(null);
    setErrorLotes(null);
    setCargandoLotes(false);
    if (!emergencia) return;

    setCargandoLotes(true);
    listarLotes(emergencia.id)
      .then((data) => !cancelado && setLotes(data.filter((l) => l.estado === 'PUBLICADO')))
      .catch((err: unknown) => !cancelado && setErrorLotes(mensajeDeError(err, 'No se pudieron cargar los lotes.')))
      .finally(() => !cancelado && setCargandoLotes(false));
    return () => { cancelado = true; };
  }, [emergencia]);

  function seleccionarEmergencia(id: number | null) {
    setLotes([]);
    setLoteId(null);
    setEmergenciaId(id);
  }

  async function guardarOferta(request: CrearOfertaRequest) {
    if (!lote) throw new Error('Elegí un lote publicado.');
    setGuardando(true);
    try {
      return await crearOferta(lote.id, request);
    } finally {
      setGuardando(false);
    }
  }

  return (
    <section className="ofertas-page">
      <h2>Registrar oferta de ayuda</h2>
      <p>Elegí una convocatoria y un lote publicado para ofrecer los recursos o el personal que tu ONG puede aportar.</p>

      <div className="emergencia-picker">
        <div className="form-field">
          <label htmlFor="emergenciaOferta">Convocatoria</label>
          <select
            id="emergenciaOferta"
            value={emergenciaId ?? ''}
            disabled={cargandoEmergencias || guardando}
            onChange={(e) => seleccionarEmergencia(e.target.value ? Number(e.target.value) : null)}
          >
            <option value="">
              {cargandoEmergencias ? 'Cargando…' : 'Elegí una convocatoria…'}
            </option>
            {emergencias.map((e) => (
              <option key={e.id} value={e.id}>
                #{e.id} · {TIPO_DESASTRE_LABELS[e.tipoDesastre]} en {e.zonaAfectada} ({e.municipio})
              </option>
            ))}
          </select>
        </div>
        <button
          type="button"
          className="secondary"
          disabled={cargandoEmergencias || cargandoLotes || guardando}
          onClick={() => {
            setCargandoEmergencias(true);
            setLotes([]);
            setLoteId(null);
            setRecarga((prev) => prev + 1);
          }}
        >
          Actualizar
        </button>
      </div>

      {errorEmergencias && <p className="form-banner error" role="alert">{errorEmergencias}</p>}
      {!cargandoEmergencias && !errorEmergencias && emergencias.length === 0 && (
        <p className="form-banner info">No hay convocatorias publicadas. Podrás ofertar cuando el Centro Coordinador publique los lotes.</p>
      )}

      {!cargandoEmergencias && emergencia && (
        <>
          <div className="emergencia-resumen">
            <strong>Emergencia #{emergencia.id} · {TIPO_DESASTRE_LABELS[emergencia.tipoDesastre]}</strong>{' '}
            <span className={`badge gravedad-${emergencia.nivelGravedad.toLowerCase()}`}>
              Gravedad {NIVEL_GRAVEDAD_LABELS[emergencia.nivelGravedad]}
            </span>
            <div className="muted">{emergencia.municipio} — {emergencia.zonaAfectada}</div>
            <p>{emergencia.descripcion}</p>
          </div>

          {errorLotes && <p className="form-banner error" role="alert">{errorLotes}</p>}
          {cargandoLotes ? (
            <p className="muted" role="status">Cargando lotes…</p>
          ) : !errorLotes && lotes.length === 0 ? (
            <p className="form-banner info">Esta convocatoria no tiene lotes publicados que admitan ofertas.</p>
          ) : !errorLotes && (
            <div className="form-field">
              <label htmlFor="loteOferta">Lote publicado</label>
              <select
                id="loteOferta"
                value={loteId ?? ''}
                disabled={guardando}
                onChange={(e) => setLoteId(e.target.value ? Number(e.target.value) : null)}
              >
                <option value="">Elegí un lote…</option>
                {lotes.map((l) => (
                  <option key={l.id} value={l.id}>
                    #{l.id} · {TIPO_RECURSO_LABELS[l.tipoRecurso]} — {l.cantidadRequerida.toLocaleString('es-AR')} {l.unidadMedida}
                  </option>
                ))}
              </select>
            </div>
          )}

          {!cargandoLotes && lote && (
            <>
              <div className="emergencia-resumen">
                <strong>Lote #{lote.id} · {TIPO_RECURSO_LABELS[lote.tipoRecurso]}</strong>
                <p>{lote.descripcion}</p>
                <p>Cantidad requerida: {lote.cantidadRequerida.toLocaleString('es-AR')} {lote.unidadMedida}.</p>
              </div>
              <OfertaForm key={lote.id} lote={lote} onSubmit={guardarOferta} />
            </>
          )}
        </>
      )}
    </section>
  );
}
