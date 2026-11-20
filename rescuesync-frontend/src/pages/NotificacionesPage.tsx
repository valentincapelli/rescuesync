
import { useCallback, useEffect, useState } from 'react';
import './NotificacionesPage.css';

import { ApiError } from '../api/client';
import {
  obtenerNotificaciones,
  marcarComoLeida,
  type NotificacionResponse,
} from '../api/notificaciones';

interface NotificacionesPageProps {
  onNotificacionesChange?: () => void;
}

function mensajeDeError(err: unknown, fallback: string): string {
  return err instanceof ApiError ? err.message : fallback;
}

function formatearFecha(fecha: string): string {
  return new Intl.DateTimeFormat('es-AR', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(fecha));
}

export function NotificacionesPage({
  onNotificacionesChange,
}: NotificacionesPageProps) {
  const [notificaciones, setNotificaciones] = useState<
    NotificacionResponse[]
  >([]);
  const [cargando, setCargando] = useState(true);
  const [actualizando, setActualizando] = useState(false);
  const [marcandoId, setMarcandoId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [recarga, setRecarga] = useState(0);

  const cargarNotificaciones = useCallback(async () => {
    setError(null);

    try {
      const data = await obtenerNotificaciones();
      setNotificaciones(data);
    } catch (err: unknown) {
      setError(
        mensajeDeError(
          err,
          'No se pudieron cargar las notificaciones.',
        ),
      );
    } finally {
      setCargando(false);
      setActualizando(false);
    }
  }, []);

  useEffect(() => {
    void cargarNotificaciones();
  }, [cargarNotificaciones, recarga]);

  async function handleMarcarComoLeida(
    notificacion: NotificacionResponse,
  ) {
    if (notificacion.leida || marcandoId !== null) return;

    setMarcandoId(notificacion.id);
    setError(null);

    try {
      await marcarComoLeida(notificacion.id);

      setNotificaciones((actuales) =>
        actuales.map((n) =>
          n.id === notificacion.id
            ? { ...n, leida: true }
            : n,
        ),
      );

      onNotificacionesChange?.();
    } catch (err: unknown) {
      setError(
        mensajeDeError(
          err,
          'No se pudo marcar la notificación como leída.',
        ),
      );
    } finally {
      setMarcandoId(null);
    }
  }

  function actualizar() {
    setActualizando(true);
    setRecarga((prev) => prev + 1);
  }

  const noLeidas = notificaciones.filter((n) => !n.leida).length;

  return (
    <section className="notificaciones-page">
      <div className="notificaciones-header">
        <div>
          <h2>Notificaciones</h2>
          <p>
            Consultá las novedades sobre las ofertas presentadas por tu ONG.
          </p>
        </div>

        <button
          type="button"
          className="secondary"
          onClick={actualizar}
          disabled={cargando || actualizando || marcandoId !== null}
        >
          {actualizando ? 'Actualizando…' : 'Actualizar'}
        </button>
      </div>

      {!cargando && !error && (
        <div className="notificaciones-resumen">
          <span>
            {notificaciones.length}{' '}
            {notificaciones.length === 1
              ? 'notificación'
              : 'notificaciones'}
          </span>

          <span className="notificaciones-pendientes">
            {noLeidas} sin leer
          </span>
        </div>
      )}

      {error && (
        <p className="form-banner error" role="alert">
          {error}
        </p>
      )}

      {cargando ? (
        <p className="muted" role="status">
          Cargando notificaciones…
        </p>
      ) : !error && notificaciones.length === 0 ? (
        <p className="form-banner info">
          Todavía no tenés notificaciones. Cuando se evalúen tus ofertas,
          recibirás novedades en esta sección.
        </p>
      ) : !error && (
        <div className="notificaciones-lista">
          {notificaciones.map((notificacion) => {
            const seleccionada =
              notificacion.tipo === 'OFERTA_SELECCIONADA';

            return (
              <article
                key={notificacion.id}
                className={`notificacion-card ${
                  notificacion.leida ? 'leida' : 'no-leida'
                }`}
              >
                <div className="notificacion-contenido">
                  <div className="notificacion-titulo">
                    <span
                      className={`notificacion-tipo ${
                        seleccionada ? 'seleccionada' : 'rechazada'
                      }`}
                    >
                      {seleccionada
                        ? 'Oferta seleccionada'
                        : 'Oferta rechazada'}
                    </span>

                    {!notificacion.leida && (
                      <span className="notificacion-nueva">
                        Nueva
                      </span>
                    )}
                  </div>

                  <h3>{notificacion.titulo}</h3>

                  <p>{notificacion.mensaje}</p>

                  <time dateTime={notificacion.fechaCreacion}>
                    {formatearFecha(notificacion.fechaCreacion)}
                  </time>
                </div>

                {!notificacion.leida && (
                  <button
                    type="button"
                    className="notificacion-marcar-leida"
                    onClick={() =>
                      void handleMarcarComoLeida(notificacion)
                    }
                    disabled={marcandoId !== null}
                  >
                    {marcandoId === notificacion.id
                      ? 'Guardando…'
                      : 'Marcar como leída'}
                  </button>
                )}
              </article>
            );
          })}
        </div>
      )}
    </section>
  );
}
