import { useEffect, useState } from 'react';
import './App.css';
import { fetchInfo, type InfoResponse } from './api/info';

type ConnectionState = 'loading' | 'up' | 'down';

function App() {
  const [state, setState] = useState<ConnectionState>('loading');
  const [info, setInfo] = useState<InfoResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchInfo()
      .then((data) => {
        setInfo(data);
        setState('up');
      })
      .catch((err: unknown) => {
        setError(err instanceof Error ? err.message : 'Error desconocido');
        setState('down');
      });
  }, []);

  return (
    <>
      <h1>RescueSync</h1>
      <p>Coordinación de emergencias — DSSD 2026, Grupo 21.</p>

      <div className="status-card">
        <span className={`status-dot ${state}`} />
        {state === 'loading' && <span>Conectando con el backend…</span>}
        {state === 'up' && info && (
          <span>
            Backend {info.application} v{info.version} — {info.status}
          </span>
        )}
        {state === 'down' && <span>No se pudo conectar al backend</span>}
      </div>

      {state === 'down' && (
        <p className="status-details">
          {error} — ¿Está corriendo <code>mvn spring-boot:run</code> en{' '}
          <code>rescuesync-backend</code>?
        </p>
      )}
      {state === 'up' && info && (
        <p className="status-details">Hora del servidor: {info.serverTime}</p>
      )}
    </>
  );
}

export default App;
