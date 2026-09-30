import { useState } from 'react';
import './App.css';
import { BackendStatus } from './components/BackendStatus';
import { AltaEmergenciaPage } from './pages/AltaEmergenciaPage';
import { LotesPage } from './pages/LotesPage';
import { OfertasPage } from './pages/OfertasPage';

// Sin login todavía (E2-10): una sección por rol del proceso (ver plan.md, P8).
const SECCIONES = [
  { id: 'municipio', label: 'Municipio · Registrar emergencia' },
  { id: 'ccr', label: 'Centro Coordinador · Lotes' },
  { id: 'ong', label: 'ONG · Ofertas' },
] as const;
type Seccion = (typeof SECCIONES)[number]['id'];

function App() {
  const [seccion, setSeccion] = useState<Seccion>('municipio');

  return (
    <>
      <h1>RescueSync</h1>
      <p>Coordinación de emergencias — DSSD 2026, Grupo 21.</p>

      <BackendStatus />

      <nav className="tabs" aria-label="Secciones">
        {SECCIONES.map((s) => (
          <button
            key={s.id}
            type="button"
            className={seccion === s.id ? 'active' : undefined}
            aria-current={seccion === s.id ? 'page' : undefined}
            onClick={() => setSeccion(s.id)}
          >
            {s.label}
          </button>
        ))}
      </nav>

      {seccion === 'municipio' && <AltaEmergenciaPage />}
      {seccion === 'ccr' && <LotesPage />}
      {seccion === 'ong' && <OfertasPage />}
    </>
  );
}

export default App;
