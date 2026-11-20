import { useEffect, useRef, useState } from 'react';
import './AltaEmergenciaPage.css';
import './LotesPage.css';
import './DesplieguesPage.css';
import { ApiError } from '../api/client';
import { listarEmergencias } from '../api/emergencias';
import { listarDespliegues, listarSelecciones, registrarSeguimiento } from '../api/despliegues';
import { ActividadDespliegue } from '../components/despliegues/ActividadDespliegue';
import type { Emergencia } from '../types/emergencia';
import type { Despliegue, SeleccionOfertaEstado, TipoNovedad } from '../types/despliegue';

function estadoSeleccion(s: SeleccionOfertaEstado): string {
  if (s.estadoIntegracion === 'INCIDENTE') return 'Incidente de integración';
  if (s.liberadoEn) return 'Liberación nacional confirmada';
  if (s.finalizadoEn) return 'Actividad finalizada · Liberación pendiente';
  if (s.comprometidoEn) return 'Compromiso nacional confirmado · Actividad en ejecución';
  if (s.estadoIntegracion === 'EN_CICLO') return 'Pendiente de confirmación del compromiso nacional';
  if (s.estadoIntegracion === 'DESPACHANDO') return 'Pendiente de sincronización con Bonita';
  return 'Adjudicación registrada · Esperando tarea de Bonita';
}

export function DesplieguesPage({ esOng = false }: { esOng?: boolean }) {
  const [emergencias, setEmergencias] = useState<Emergencia[]>([]);
  const [emergenciaId, setEmergenciaId] = useState<number | null>(null);
  const [actividades, setActividades] = useState<Despliegue[]>([]);
  const [selecciones, setSelecciones] = useState<SeleccionOfertaEstado[]>([]);
  const [cargando, setCargando] = useState(true);
  const [guardando, setGuardando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [aviso, setAviso] = useState<string | null>(null);
  const [recarga, setRecarga] = useState(0);
  const [actualizar, setActualizar] = useState(false);
  const montado = useRef(true);
  const envioEnCurso = useRef(false);
  const ocupado = cargando || guardando;

  useEffect(() => {
    montado.current = true;
    return () => { montado.current = false; };
  }, []);

  useEffect(() => {
    if (esOng) return;
    let cancelado = false;
    listarEmergencias()
      .then((data) => { if (!cancelado) setEmergencias(data.sort((a, b) => b.id - a.id)); })
      .catch((err: unknown) => { if (!cancelado) setError(err instanceof ApiError ? err.message : 'No se pudieron cargar las emergencias.'); });
    return () => { cancelado = true; };
  }, [esOng, recarga]);

  useEffect(() => {
    let cancelado = false;
    setError(null);
    if (!esOng && emergenciaId === null) { setCargando(false); return; }
    setCargando(true);
    listarDespliegues(esOng ? null : emergenciaId, actualizar)
      .then(async (data) => {
        const estados = !esOng && emergenciaId !== null ? await listarSelecciones(emergenciaId) : [];
        if (!cancelado) { setActividades(data); setSelecciones(estados); }
      })
      .catch((err: unknown) => {
        if (!cancelado) setError(err instanceof ApiError ? err.message : 'No se pudo consultar el seguimiento.');
      })
      .finally(() => { if (!cancelado) setCargando(false); });
    return () => { cancelado = true; };
  }, [emergenciaId, esOng, recarga, actualizar]);

  async function guardar(ofertaId: number, tipo: TipoNovedad, observacion: string) {
    if (envioEnCurso.current) return false;
    envioEnCurso.current = true;
    setGuardando(true);
    setError(null);
    setAviso(null);
    try {
      const resultado = await registrarSeguimiento(ofertaId, tipo, observacion);
      if (!montado.current) return false;
      setActividades((prev) => prev.map((d) => d.ofertaId === resultado.ofertaId ? resultado : d));
      setAviso(resultado.pendienteSincronizacion ? 'Solicitud registrada. Esperando confirmación.' : 'Seguimiento actualizado.');
      setActualizar(false);
      setRecarga((n) => n + 1);
      return true;
    } catch (err) {
      if (montado.current) setError(err instanceof ApiError ? err.message : 'No se pudo guardar el seguimiento.');
      return false;
    } finally {
      envioEnCurso.current = false;
      if (montado.current) setGuardando(false);
    }
  }

  return <section className="despliegues-page">
    <h2>{esOng ? 'Mis actividades de asistencia' : 'Monitorear despliegue'}</h2>
    <p>{esOng ? 'Consultá las actividades de tu ONG e informá cuándo concluye la asistencia.'
      : 'Supervisá la ayuda comprometida, registrá novedades y completá el monitoreo para que la ONG informe la finalización.'}</p>
    <div className="emergencia-picker">
      {!esOng && <div className="form-field">
        <label htmlFor="emergencia-despliegue">Emergencia</label>
        <select id="emergencia-despliegue" value={emergenciaId ?? ''} disabled={ocupado}
          onChange={(e) => {
            setActividades([]); setSelecciones([]); setAviso(null); setActualizar(false);
            setEmergenciaId(e.target.value ? Number(e.target.value) : null);
          }}>
          <option value="">Elegí una emergencia…</option>
          {emergencias.map((e) => <option key={e.id} value={e.id}>#{e.id} · {e.municipio} — {e.zonaAfectada}</option>)}
        </select>
      </div>}
      <button className="secondary" type="button" disabled={ocupado}
        onClick={() => { setAviso(null); setActualizar(true); setRecarga((n) => n + 1); }}>
        {cargando ? 'Actualizando…' : 'Actualizar'}
      </button>
    </div>
    {error && <p className="form-banner error" role="alert">{error}</p>}
    {aviso && <p className="form-banner success" role="status">{aviso}</p>}
    {cargando && <p className="muted" role="status">Consultando actividades…</p>}
    {!esOng && selecciones.length > 0 && <div className="despliegue-lista" aria-label="Estado de ofertas adjudicadas">
      {selecciones.filter((s) => s.estadoIntegracion === 'INCIDENTE'
        || !actividades.some((d) => d.ofertaId === s.ofertaId)).map((s) => <article className="actividad-despliegue" key={s.ofertaId}>
        <h3>Oferta #{s.ofertaId} · {s.ong}</h3>
        <span className="badge">{estadoSeleccion(s)}</span>
        {s.detalleIncidente && <p className="form-banner error" role="alert">{s.detalleIncidente}</p>}
      </article>)}
    </div>}
    {!cargando && !error && actividades.length === 0 && (esOng || emergenciaId !== null) &&
      <p className="form-banner info" role="status">No hay despliegues con compromiso confirmado. Usá Actualizar para consultar los últimos avances del proceso.</p>}
    <div className="despliegue-lista" aria-busy={ocupado}>
      {actividades.map((d) => <ActividadDespliegue key={d.ofertaId} actividad={d} esOng={esOng}
        ocupado={ocupado} onGuardar={guardar} />)}
    </div>
  </section>;
}
