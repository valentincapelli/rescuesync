import { useState } from 'react';
import './App.css';

import { BackendStatus } from './components/BackendStatus';
import { AltaEmergenciaPage } from './pages/AltaEmergenciaPage';
import { LotesPage } from './pages/LotesPage';
import { OfertasPage } from './pages/OfertasPage';
import { LoginPage } from './pages/LoginPage';

import { useAuth } from './context/AuthContext';
import type { Rol } from './types/auth';

const SECCIONES = [
  {
    id: 'municipio',
    label: 'Municipio · Registrar emergencia',
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
          </button>
        ))}
      </nav>

      {seccionActual === 'municipio' && (
        <AltaEmergenciaPage />
      )}

      {seccionActual === 'ccr' && <LotesPage />}

      {seccionActual === 'ong' && <OfertasPage />}
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

    default:
      return null;
  }
}

export default App;