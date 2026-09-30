import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { ApiError } from '../api/client';
import {
  TIPO_RECURSO_LABELS,
  TIPOS_RECURSO,
  UNIDADES_SUGERIDAS,
  type Lote,
  type LoteRequest,
  type TipoRecurso,
} from '../types/lote';

// El input numérico se maneja como string para poder dejarlo vacío mientras se tipea.
interface LoteFormState {
  tipoRecurso: TipoRecurso;
  descripcion: string;
  cantidadRequerida: string;
  unidadMedida: string;
}

type FieldErrors = Partial<Record<keyof LoteFormState, string>>;

const emptyForm: LoteFormState = {
  tipoRecurso: TIPOS_RECURSO[0],
  descripcion: '',
  cantidadRequerida: '',
  unidadMedida: UNIDADES_SUGERIDAS[TIPOS_RECURSO[0]][0],
};

function fromLote(lote: Lote): LoteFormState {
  return {
    tipoRecurso: lote.tipoRecurso,
    descripcion: lote.descripcion,
    cantidadRequerida: String(lote.cantidadRequerida),
    unidadMedida: lote.unidadMedida,
  };
}

// Mismas reglas que CrearLoteRequest en el backend.
function validate(form: LoteFormState): FieldErrors {
  const errors: FieldErrors = {};

  if (!form.descripcion.trim()) {
    errors.descripcion = 'Describí qué se necesita (ej: "Paramédicos con equipo propio").';
  } else if (form.descripcion.length > 300) {
    errors.descripcion = 'Máximo 300 caracteres.';
  }

  const cantidad = Number(form.cantidadRequerida);
  if (!form.cantidadRequerida.trim()) {
    errors.cantidadRequerida = 'Indicá la cantidad requerida.';
  } else if (!Number.isInteger(cantidad) || cantidad <= 0) {
    errors.cantidadRequerida = 'Debe ser un número entero mayor a 0.';
  } else if (cantidad > 2_147_483_647) {
    errors.cantidadRequerida = 'Cantidad demasiado grande.';
  }

  if (!form.unidadMedida.trim()) {
    errors.unidadMedida = 'Indicá la unidad (personas, raciones, litros…).';
  } else if (form.unidadMedida.length > 30) {
    errors.unidadMedida = 'Máximo 30 caracteres.';
  }

  return errors;
}

interface Props {
  /** Lote que se está editando; null para dar de alta uno nuevo. */
  editando: Lote | null;
  onSubmit: (request: LoteRequest) => Promise<void>;
  onCancelEdit: () => void;
}

export function LoteForm({ editando, onSubmit, onCancelEdit }: Props) {
  const [form, setForm] = useState<LoteFormState>(emptyForm);
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [submitting, setSubmitting] = useState(false);
  const [generalError, setGeneralError] = useState<string | null>(null);

  // Al elegir "Editar" en la tabla se precarga el formulario.
  useEffect(() => {
    setForm(editando ? fromLote(editando) : emptyForm);
    setFieldErrors({});
    setGeneralError(null);
  }, [editando]);

  function updateField<K extends keyof LoteFormState>(field: K, value: LoteFormState[K]) {
    setForm((prev) => ({ ...prev, [field]: value }));
  }

  function changeTipo(tipo: TipoRecurso) {
    setForm((prev) => {
      // Si la unidad era una sugerencia del tipo anterior (o estaba vacía), sugerimos la del nuevo tipo.
      const eraSugerencia = !prev.unidadMedida.trim() || UNIDADES_SUGERIDAS[prev.tipoRecurso].includes(prev.unidadMedida);
      return {
        ...prev,
        tipoRecurso: tipo,
        unidadMedida: eraSugerencia ? UNIDADES_SUGERIDAS[tipo][0] : prev.unidadMedida,
      };
    });
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setGeneralError(null);

    const errors = validate(form);
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) {
      return;
    }

    setSubmitting(true);
    try {
      await onSubmit({
        tipoRecurso: form.tipoRecurso,
        descripcion: form.descripcion.trim(),
        cantidadRequerida: Number(form.cantidadRequerida),
        unidadMedida: form.unidadMedida.trim(),
      });
      if (!editando) {
        // Alta OK: dejamos el tipo elegido para cargar varios lotes seguidos rápido.
        setForm((prev) => ({ ...emptyForm, tipoRecurso: prev.tipoRecurso, unidadMedida: prev.unidadMedida }));
      }
      setFieldErrors({});
    } catch (err) {
      if (err instanceof ApiError && err.fieldErrors) {
        setFieldErrors(err.fieldErrors as FieldErrors);
      } else if (err instanceof ApiError) {
        setGeneralError(err.message);
      } else {
        setGeneralError('No se pudo guardar el lote. Intentá de nuevo.');
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form className="emergencia-form lote-form" onSubmit={handleSubmit} noValidate>
      <h3>{editando ? `Editar lote #${editando.id}` : 'Agregar lote'}</h3>

      {generalError && <p className="form-banner error">{generalError}</p>}

      <div className="form-row">
        <div className={`form-field ${fieldErrors.tipoRecurso ? 'has-error' : ''}`}>
          <label htmlFor="tipoRecurso">Tipo de recurso</label>
          <select
            id="tipoRecurso"
            value={form.tipoRecurso}
            onChange={(e) => changeTipo(e.target.value as TipoRecurso)}
          >
            {TIPOS_RECURSO.map((tipo) => (
              <option key={tipo} value={tipo}>
                {TIPO_RECURSO_LABELS[tipo]}
              </option>
            ))}
          </select>
          {fieldErrors.tipoRecurso && <span className="field-error">{fieldErrors.tipoRecurso}</span>}
        </div>

        <div className={`form-field narrow ${fieldErrors.cantidadRequerida ? 'has-error' : ''}`}>
          <label htmlFor="cantidadRequerida">Cantidad</label>
          <input
            id="cantidadRequerida"
            type="number"
            min={1}
            step={1}
            inputMode="numeric"
            value={form.cantidadRequerida}
            onChange={(e) => updateField('cantidadRequerida', e.target.value)}
            placeholder="Ej: 1000"
          />
          {fieldErrors.cantidadRequerida && <span className="field-error">{fieldErrors.cantidadRequerida}</span>}
        </div>

        <div className={`form-field narrow ${fieldErrors.unidadMedida ? 'has-error' : ''}`}>
          <label htmlFor="unidadMedida">Unidad</label>
          <input
            id="unidadMedida"
            type="text"
            list="unidades-sugeridas"
            maxLength={30}
            value={form.unidadMedida}
            onChange={(e) => updateField('unidadMedida', e.target.value)}
          />
          <datalist id="unidades-sugeridas">
            {UNIDADES_SUGERIDAS[form.tipoRecurso].map((u) => (
              <option key={u} value={u} />
            ))}
          </datalist>
          {fieldErrors.unidadMedida && <span className="field-error">{fieldErrors.unidadMedida}</span>}
        </div>
      </div>

      <div className={`form-field ${fieldErrors.descripcion ? 'has-error' : ''}`}>
        <label htmlFor="descripcionLote">Descripción</label>
        <input
          id="descripcionLote"
          type="text"
          maxLength={300}
          value={form.descripcion}
          onChange={(e) => updateField('descripcion', e.target.value)}
          placeholder="Ej: Paramédicos con equipo propio / Raciones de comida caliente"
        />
        {fieldErrors.descripcion && <span className="field-error">{fieldErrors.descripcion}</span>}
      </div>

      <div className="form-actions">
        <button type="submit" disabled={submitting}>
          {submitting ? 'Guardando…' : editando ? 'Guardar cambios' : 'Agregar lote'}
        </button>
        {editando && (
          <button type="button" className="secondary" onClick={onCancelEdit} disabled={submitting}>
            Cancelar
          </button>
        )}
      </div>
    </form>
  );
}
