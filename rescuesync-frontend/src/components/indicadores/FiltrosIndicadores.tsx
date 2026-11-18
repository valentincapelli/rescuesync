import { useState, type FormEvent } from 'react';
import { TIPO_DESASTRE_LABELS, TIPOS_DESASTRE, type TipoDesastre } from '../../types/emergencia';
import type { FiltroIndicadores } from '../../types/indicadores';
import { TIPO_RECURSO_LABELS, TIPOS_RECURSO, type TipoRecurso } from '../../types/lote';

interface Props {
  aplicado: FiltroIndicadores;
  onAplicar: (filtro: FiltroIndicadores) => void;
}

interface Borrador {
  desde: string;
  hasta: string;
  municipioId: string;
  tipoDesastre: TipoDesastre | '';
  tipoRecurso: TipoRecurso | '';
}

function aBorrador(f: FiltroIndicadores): Borrador {
  return {
    desde: f.desde ?? '',
    hasta: f.hasta ?? '',
    municipioId: f.municipioId !== undefined ? String(f.municipioId) : '',
    tipoDesastre: f.tipoDesastre ?? '',
    tipoRecurso: f.tipoRecurso ?? '',
  };
}

/** Barra de filtros del dashboard. Los cambios se aplican recién con "Aplicar". */
export function FiltrosIndicadores({ aplicado, onAplicar }: Props) {
  const [borrador, setBorrador] = useState<Borrador>(() => aBorrador(aplicado));
  const [error, setError] = useState<string | null>(null);

  function cambiar<K extends keyof Borrador>(campo: K, valor: Borrador[K]) {
    setBorrador((prev) => ({ ...prev, [campo]: valor }));
    setError(null);
  }

  function aplicar(e: FormEvent) {
    e.preventDefault();
    if (borrador.desde && borrador.hasta && borrador.desde > borrador.hasta) {
      setError('La fecha "desde" no puede ser posterior a "hasta".');
      return;
    }
    let municipioId: number | undefined;
    if (borrador.municipioId.trim() !== '') {
      municipioId = Number(borrador.municipioId);
      if (!Number.isInteger(municipioId) || municipioId <= 0) {
        setError('El id de municipio tiene que ser un número entero positivo.');
        return;
      }
    }
    const filtro: FiltroIndicadores = {};
    if (borrador.desde) filtro.desde = borrador.desde;
    if (borrador.hasta) filtro.hasta = borrador.hasta;
    if (municipioId !== undefined) filtro.municipioId = municipioId;
    if (borrador.tipoDesastre) filtro.tipoDesastre = borrador.tipoDesastre;
    if (borrador.tipoRecurso) filtro.tipoRecurso = borrador.tipoRecurso;
    onAplicar(filtro);
  }

  function limpiar() {
    setBorrador(aBorrador({}));
    setError(null);
    onAplicar({});
  }

  return (
    <form className="ind-filtros" onSubmit={aplicar} aria-label="Filtros de indicadores">
      <div className="ind-filtros-campos">
        <div className="form-field">
          <label htmlFor="ind-desde">Desde</label>
          <input id="ind-desde" type="date" value={borrador.desde} onChange={(e) => cambiar('desde', e.target.value)} />
        </div>
        <div className="form-field">
          <label htmlFor="ind-hasta">Hasta</label>
          <input id="ind-hasta" type="date" value={borrador.hasta} onChange={(e) => cambiar('hasta', e.target.value)} />
        </div>
        <div className="form-field">
          <label htmlFor="ind-desastre">Tipo de desastre</label>
          <select
            id="ind-desastre"
            value={borrador.tipoDesastre}
            onChange={(e) => cambiar('tipoDesastre', e.target.value as TipoDesastre | '')}
          >
            <option value="">Todos</option>
            {TIPOS_DESASTRE.map((t) => (
              <option key={t} value={t}>
                {TIPO_DESASTRE_LABELS[t]}
              </option>
            ))}
          </select>
        </div>
        <div className="form-field">
          <label htmlFor="ind-recurso">Tipo de recurso</label>
          <select
            id="ind-recurso"
            value={borrador.tipoRecurso}
            onChange={(e) => cambiar('tipoRecurso', e.target.value as TipoRecurso | '')}
            aria-describedby="ind-recurso-nota"
          >
            <option value="">Todos</option>
            {TIPOS_RECURSO.map((t) => (
              <option key={t} value={t}>
                {TIPO_RECURSO_LABELS[t]}
              </option>
            ))}
          </select>
        </div>
        <div className="form-field ind-campo-angosto">
          <label htmlFor="ind-municipio">Municipio (id)</label>
          <input
            id="ind-municipio"
            type="number"
            min={1}
            step={1}
            inputMode="numeric"
            placeholder="Todos"
            value={borrador.municipioId}
            onChange={(e) => cambiar('municipioId', e.target.value)}
          />
        </div>
        <div className="form-actions ind-filtros-botones">
          <button type="submit">Aplicar</button>
          <button type="button" className="secondary" onClick={limpiar}>
            Limpiar
          </button>
        </div>
      </div>
      <p id="ind-recurso-nota" className="muted ind-nota">
        El tipo de recurso solo afecta a suministros movilizados y a consorcios. Si no se eligen fechas, se usan
        los últimos 12 meses.
      </p>
      {error && (
        <p className="form-banner error" role="alert">
          {error}
        </p>
      )}
    </form>
  );
}
