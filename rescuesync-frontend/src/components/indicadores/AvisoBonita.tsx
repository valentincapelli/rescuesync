import type { EstadoFuenteBonita } from '../../types/indicadores';

/** Aviso visible cuando Bonita no respondió: los datos que dependen de él quedan "Sin datos". */
export function AvisoBonita({ bonita }: { bonita: EstadoFuenteBonita }) {
  if (bonita.disponible) return null;
  return (
    <div className="form-banner ind-aviso-bonita" role="status">
      <strong>Bonita no disponible.</strong>{' '}
      {bonita.mensaje ?? 'No se pudieron obtener los datos del motor de procesos.'} Los tiempos de
      resolución y por etapa se muestran sin datos; el resto de los indicadores no se ve afectado.
    </div>
  );
}
