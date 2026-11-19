import { lazy, Suspense, useState, useEffect } from 'react';
import './App.css';
import { contarNoLeidas } from './api/notificaciones';
import { NotificacionesPage } from './pages/NotificacionesPage';

import { BackendStatus } from './components/BackendStatus';
import { AltaEmergenciaPage } from './pages/AltaEmergenciaPage';
import { LotesPage } from './pages/LotesPage';
import { OfertasPage } from './pages/OfertasPage';
import { SeleccionOfertasPage } from './pages/SeleccionOfertasPage';
import { LoginPage } from './pages/LoginPage';
import { DesplieguesPage } from './pages/DesplieguesPage';

import { useAuth } from './context/AuthContext';
import type { Rol } from './types/auth';

// El dashboard trae recharts: se carga aparte para no agrandar el bundle de las otras secciones.
const IndicadoresPage = lazy(() =>
  import('./pages/IndicadoresPage').then((m) => ({ default: m.IndicadoresPage })),
);

const SECCIONES = [
  {
    id: 'municipio',
    label: 'Municipio · Registrar emergencia',
    rol: 'OPERADOR_MUNICIPAL',
  },
  {
    id: 'seleccion-ofertas',
    label: 'Municipio · Seleccionar ofertas',
    rol: 'OPERADOR_MUNICIPAL',
  },
  {
    id: 'ccr',
    label: 'Centro Coordinador · Lotes',
    rol: 'CENTRO_COORDINADOR',
  },
  {
    id: 'ong',
    label: 'ONG · Ofertas',
    rol: 'REPRESENTANTE_ONG',
  },
  {
    id: 'notificaciones',
    label: 'ONG · Notificaciones',
    rol: 'REPRESENTANTE_ONG',
  },
    id: 'despliegues',
    label: 'Centro Coordinador · Despliegues',
    rol: 'CENTRO_COORDINADOR',
  },
  {
    id: 'actividades',
    label: 'ONG · Actividades',
    rol: 'REPRESENTANTE_ONG',
  },
  {
    id: 'direccion',
    label: 'Dirección · Indicadores',
    rol: 'DIRECTIVO',
  },
] as const;

type Seccion = (typeof SECCIONES)[number]['id'];

function App() {
  const {
    user,
    loading,
    login,
    logout,
  } = useAuth();

  const [seccion, setSeccion] = useState<Seccion | null>(null);
  const [notificacionesNoLeidas, setNotificacionesNoLeidas] = useState(0);

  useEffect(() => {
    if (!user || user.rol !== 'REPRESENTANTE_ONG') {
      setNotificacionesNoLeidas(0);
      return;
    }

    let activo = true;

    async function actualizarContador() {
      try {
        const cantidad = await contarNoLeidas();

        if (activo) {
          setNotificacionesNoLeidas(cantidad);
        }
      } catch (error) {
        console.error('Error al consultar notificaciones:', error);
      }
    }

    void actualizarContador();

    // Actualizar el contador cada 60 segundos.
    const intervalo = window.setInterval(
      () => void actualizarContador(),
      60_000,
    );

    return () => {
      activo = false;
      window.clearInterval(intervalo);
    };
  }, [user]);

  if (loading) {
    return <p>Cargando sesión...</p>;
  }

  if (!user) {
    return <LoginPage onLogin={login} />;
  }

  const rol: Rol = user.rol;

  const seccionesDisponibles = SECCIONES.filter(
    (s) => s.rol === rol,
  );

  const seccionActual =
    seccion ?? getSeccionInicial(rol);

  async function handleLogout() {
    await logout();
    setSeccion(null);
  }

  return (
    <>
      <h1>RescueSync</h1>

      <p>
        Coordinación de emergencias — DSSD 2026, Grupo 21.
      </p>

      <p>Usuario: {user.email}</p>
      <p>Rol: {user.rol}</p>

      <button
        type="button"
        onClick={() => void handleLogout()}
      >
        Cerrar sesión
      </button>

      <BackendStatus />

      <nav className="tabs" aria-label="Secciones">
        {seccionesDisponibles.map((s) => (
          <button
            key={s.id}
            type="button"
            className={
              seccionActual === s.id
                ? 'active'
                : undefined
            }
            aria-current={
              seccionActual === s.id
                ? 'page'
                : undefined
            }
            onClick={() => setSeccion(s.id)}
          >
            {s.label}

            {s.id === 'notificaciones' &&
              notificacionesNoLeidas > 0 && (
                <span className="notification-badge">
                  {notificacionesNoLeidas > 99
                    ? '99+'
                    : notificacionesNoLeidas}
                </span>
              )}
          </button>
        ))}
      </nav>

      {seccionActual === 'municipio' && (
        <AltaEmergenciaPage />
      )}

      {seccionActual === 'seleccion-ofertas' && (
        <SeleccionOfertasPage />
      )}

      {seccionActual === 'ccr' && <LotesPage />}

      {seccionActual === 'ong' && <OfertasPage />}
      {seccionActual === 'despliegues' && <DesplieguesPage />}
      {seccionActual === 'actividades' && <DesplieguesPage esOng />}

      {seccionActual === 'notificaciones' && (
        <NotificacionesPage
          onNotificacionesChange={() => {
            void contarNoLeidas()
              .then(setNotificacionesNoLeidas)
              .catch((err) =>
                console.error('Error al actualizar el contador:', err),
              );
          }}
        />
      )}

      {seccionActual === 'direccion' && (
        <Suspense fallback={<p>Cargando indicadores...</p>}>
          <IndicadoresPage />
        </Suspense>
      )}
    </>
  );
}

function getSeccionInicial(rol: Rol): Seccion | null {
  switch (rol) {
    case 'OPERADOR_MUNICIPAL':
      return 'municipio';

    case 'CENTRO_COORDINADOR':
      return 'ccr';

    case 'REPRESENTANTE_ONG':
      return 'ong';

    case 'DIRECTIVO':
      return 'direccion';

    default:
      return null;
  }
}

export default App;
