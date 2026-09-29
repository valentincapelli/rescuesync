import './App.css';
import { BackendStatus } from './components/BackendStatus';
import { AltaEmergenciaPage } from './pages/AltaEmergenciaPage';

function App() {
  return (
    <>
      <h1>RescueSync</h1>
      <p>Coordinación de emergencias — DSSD 2026, Grupo 21.</p>

      <BackendStatus />

      <hr />

      <AltaEmergenciaPage />
    </>
  );
}

export default App;
