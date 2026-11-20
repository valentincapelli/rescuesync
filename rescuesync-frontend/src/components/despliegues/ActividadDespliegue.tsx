import { useState } from 'react';
import { ConfirmDialog } from '../ConfirmDialog';
import type { Despliegue, TipoNovedad } from '../../types/despliegue';
import { TIPO_RECURSO_LABELS } from '../../types/lote';
import { ESTADO_EMERGENCIA_LABELS } from '../../types/emergencia';

const TIPOS: Record<TipoNovedad, string> = {
  NOVEDAD: 'Novedad de despliegue',
  MONITOREO_COMPLETADO: 'Monitoreo completado',
  FINALIZACION: 'Finalización de actividad',
};
function fecha(valor: string) {
  return new Date(valor).toLocaleString('es-AR');
}
function estado(d: Despliegue) {
  if (d.pendienteSincronizacion) return 'Confirmación pendiente';
  if (d.liberadoEn) return 'Recursos liberados';
  if (d.finalizadoEn) return 'Actividad finalizada · Liberación pendiente';
  if (d.monitoreadoEn) return 'Esperando finalización de la ONG';
  return 'Despliegue en curso';
}

interface Props {
  actividad: Despliegue;
  esOng: boolean;
  ocupado: boolean;
  onGuardar: (ofertaId: number, tipo: TipoNovedad, observacion: string) => Promise<boolean>;
}

export function ActividadDespliegue({ actividad: d, esOng, ocupado, onGuardar }: Props) {
  const [observacion, setObservacion] = useState('');
  const [confirmar, setConfirmar] = useState<TipoNovedad | null>(null);
  const permiteNovedad = !esOng && !d.finalizadoEn && !d.liberadoEn;
  const permiteFormulario = permiteNovedad || d.puedeFinalizar;

  // Novedad se guarda directo; monitoreo y finalización piden confirmación en un modal.
  function guardar(tipo: TipoNovedad) {
    if (!observacion.trim() || ocupado) return;
    if (tipo === 'NOVEDAD') void ejecutar(tipo);
    else setConfirmar(tipo);
  }

  async function ejecutar(tipo: TipoNovedad) {
    setConfirmar(null);
    if (await onGuardar(d.ofertaId, tipo, observacion.trim())) setObservacion('');
  }

  return (
    <article className="actividad-despliegue">
      <div className="despliegue-encabezado">
        <h3>Oferta #{d.ofertaId} · {d.ong}</h3>
        <span className="badge">{estado(d)}</span>
      </div>
      <p className="muted">Emergencia #{d.emergenciaId} · {d.municipio} — {d.zonaAfectada} · {ESTADO_EMERGENCIA_LABELS[d.estadoEmergencia]}</p>
      <p><strong>Lote #{d.loteId} · {TIPO_RECURSO_LABELS[d.tipoRecurso]}</strong> — {d.cantidad.toLocaleString('es-AR')} {d.unidadMedida}</p>
      <p>{d.descripcion}</p>
      <dl className="despliegue-fechas">
        <div><dt>Compromiso confirmado</dt><dd>{fecha(d.comprometidoEn)}</dd></div>
        {d.monitoreadoEn && <div><dt>Monitoreo completado</dt><dd>{fecha(d.monitoreadoEn)}</dd></div>}
        {d.finalizadoEn && <div><dt>Actividad finalizada</dt><dd>{fecha(d.finalizadoEn)}</dd></div>}
        {d.liberadoEn && <div><dt>Liberación confirmada</dt><dd>{fecha(d.liberadoEn)}</dd></div>}
      </dl>

      {d.pendienteSincronizacion && <p className="form-banner info" role="status">La solicitud está registrada y espera confirmación. Usá Actualizar para consultar su resultado.</p>}
      {esOng && !d.monitoreadoEn && !d.finalizadoEn && <p className="muted">Podrás finalizar la actividad cuando el Centro Coordinador complete el monitoreo.</p>}
      {d.finalizadoEn && !d.liberadoEn && <p className="form-banner info" role="status">La actividad finalizó. La liberación de recursos todavía no fue confirmada.</p>}

      <details>
        <summary>Novedades ({d.novedades.length})</summary>
        {d.novedades.length === 0 ? <p className="muted">Todavía no se registraron novedades.</p> : (
          <ol className="despliegue-novedades">
            {d.novedades.map((n) => <li key={n.id}>
              <strong>{TIPOS[n.tipo]}{!n.confirmada && ' · Pendiente de confirmación'}</strong>
              <div className="muted">{fecha(n.fecha)} · {n.autor}</div>
              <p>{n.observacion}</p>
            </li>)}
          </ol>
        )}
      </details>

      {permiteFormulario && <form className="emergencia-form" onSubmit={(e) => {
        e.preventDefault();
        guardar(esOng ? 'FINALIZACION' : 'NOVEDAD');
      }}>
        <div className="form-field">
          <label htmlFor={`observacion-${d.ofertaId}`}>{esOng ? 'Observación de cierre' : 'Novedad del despliegue'}</label>
          <textarea id={`observacion-${d.ofertaId}`} value={observacion} maxLength={2000} required
            disabled={ocupado || d.pendienteSincronizacion} onChange={(e) => setObservacion(e.target.value)} />
        </div>
        <div className="form-actions">
          <button type="submit" disabled={ocupado || d.pendienteSincronizacion || !observacion.trim()}>
            {esOng ? 'Finalizar actividad' : 'Registrar novedad'}
          </button>
          {!esOng && d.puedeMonitorear && <button type="button" className="secondary"
            disabled={ocupado || !observacion.trim()} onClick={() => guardar('MONITOREO_COMPLETADO')}>
            Completar monitoreo
          </button>}
        </div>
      </form>}
      {d.sincronizadoEn && <p className="muted">Última actualización: {fecha(d.sincronizadoEn)}</p>}
      {confirmar && <ConfirmDialog
        titulo={confirmar === 'FINALIZACION' ? 'Finalizar actividad' : 'Completar monitoreo'}
        mensaje={confirmar === 'FINALIZACION'
          ? '¿Confirmás que la actividad de asistencia concluyó? Esto solicitará la liberación de sus recursos.'
          : '¿Completar el monitoreo y habilitar la finalización de esta actividad por la ONG?'}
        confirmarLabel={confirmar === 'FINALIZACION' ? 'Finalizar' : 'Completar monitoreo'}
        onCancelar={() => setConfirmar(null)}
        onConfirmar={() => void ejecutar(confirmar)}
      />}
    </article>
  );
}
