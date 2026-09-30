import { useState } from 'react';
import type { FormEvent } from 'react';
import { ApiError } from '../api/client';
import type { Lote } from '../types/lote';
import { ESTADO_OFERTA_LABELS, type CrearOfertaRequest, type Oferta } from '../types/oferta';

// Como en LoteForm, el input numérico admite estar vacío mientras se tipea.
interface OfertaFormState {
  ongNombre: string;
  cantidadOfrecida: string;
  observaciones: string;
}

type FieldErrors = Partial<Record<keyof OfertaFormState, string>>;

const emptyForm: OfertaFormState = {
  ongNombre: '',
  cantidadOfrecida: '',
  observaciones: '',
};

// Mismas reglas que CrearOfertaRequest (cantidad dentro del rango de Integer).
function validate(form: OfertaFormState): FieldErrors {
  const errors: FieldErrors = {};

  if (!form.ongNombre.trim()) {
    errors.ongNombre = 'Ingresá el nombre de la ONG que ofrece la ayuda.';
  } else if (form.ongNombre.length > 150) {
    errors.ongNombre = 'Máximo 150 caracteres.';
  }

  const cantidad = Number(form.cantidadOfrecida);
  if (!form.cantidadOfrecida.trim()) {
    errors.cantidadOfrecida = 'Indicá la cantidad ofrecida.';
  } else if (!Number.isInteger(cantidad) || cantidad <= 0) {
    errors.cantidadOfrecida = 'Debe ser un número entero mayor a 0.';
  } else if (cantidad > 2_147_483_647) {
    errors.cantidadOfrecida = 'Cantidad demasiado grande.';
  }

  return errors;
}

interface Props {
  lote: Lote;
  onSubmit: (request: CrearOfertaRequest) => Promise<Oferta>;
}

export function OfertaForm({ lote, onSubmit }: Props) {
  const [form, setForm] = useState<OfertaFormState>(emptyForm);
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [submitting, setSubmitting] = useState(false);
  const [generalError, setGeneralError] = useState<string | null>(null);
  const [creada, setCreada] = useState<Oferta | null>(null);

  function updateField(field: keyof OfertaFormState, value: string) {
    setForm((prev) => ({ ...prev, [field]: value }));
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (submitting) return;
    setGeneralError(null);
    setCreada(null);

    const errors = validate(form);
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) return;

    setSubmitting(true);
    try {
      const oferta = await onSubmit({
        ongNombre: form.ongNombre.trim(),
        cantidadOfrecida: Number(form.cantidadOfrecida),
        observaciones: form.observaciones.trim() || null,
      });
      setCreada(oferta);
      setForm(emptyForm);
      setFieldErrors({});
    } catch (err) {
      if (err instanceof ApiError && err.fieldErrors) {
        setFieldErrors(err.fieldErrors as FieldErrors);
      } else if (err instanceof ApiError) {
        setGeneralError(err.message);
      } else {
        setGeneralError('No se pudo registrar la oferta. Intentá de nuevo.');
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form className="emergencia-form oferta-form" onSubmit={handleSubmit} noValidate>
      <h3>Ofertar para el lote #{lote.id}</h3>
      <p className="muted">Podés cubrir todo o parte de lo solicitado. Se admite una oferta por ONG para cada lote.</p>

      {creada && (
        <p className="form-banner success" role="status">
          Oferta #{creada.id} registrada para el lote #{creada.loteId}: {creada.ongNombre} ofrece{' '}
          {creada.cantidadOfrecida.toLocaleString('es-AR')} {lote.unidadMedida}. Estado:{' '}
          {ESTADO_OFERTA_LABELS[creada.estado]}.
        </p>
      )}
      {generalError && <p className="form-banner error" role="alert">{generalError}</p>}

      <div className={`form-field ${fieldErrors.ongNombre ? 'has-error' : ''}`}>
        <label htmlFor="ongNombre">Nombre de la ONG</label>
        <input
          id="ongNombre"
          type="text"
          maxLength={150}
          required
          disabled={submitting}
          value={form.ongNombre}
          onChange={(e) => updateField('ongNombre', e.target.value)}
          placeholder="Ej: Red de Ayuda Solidaria"
          aria-invalid={!!fieldErrors.ongNombre}
          aria-describedby={fieldErrors.ongNombre ? 'ongNombre-error' : undefined}
        />
        {fieldErrors.ongNombre && <span id="ongNombre-error" className="field-error">{fieldErrors.ongNombre}</span>}
      </div>

      <div className={`form-field ${fieldErrors.cantidadOfrecida ? 'has-error' : ''}`}>
        <label htmlFor="cantidadOfrecida">Cantidad ofrecida ({lote.unidadMedida})</label>
        <input
          id="cantidadOfrecida"
          type="number"
          min={1}
          max={2_147_483_647}
          step={1}
          inputMode="numeric"
          required
          disabled={submitting}
          value={form.cantidadOfrecida}
          onChange={(e) => updateField('cantidadOfrecida', e.target.value)}
          placeholder="Ej: 100"
          aria-invalid={!!fieldErrors.cantidadOfrecida}
          aria-describedby={fieldErrors.cantidadOfrecida ? 'cantidadOfrecida-error' : undefined}
        />
        {fieldErrors.cantidadOfrecida && <span id="cantidadOfrecida-error" className="field-error">{fieldErrors.cantidadOfrecida}</span>}
      </div>

      <div className={`form-field ${fieldErrors.observaciones ? 'has-error' : ''}`}>
        <label htmlFor="observaciones">Observaciones (opcional)</label>
        <textarea
          id="observaciones"
          disabled={submitting}
          value={form.observaciones}
          onChange={(e) => updateField('observaciones', e.target.value)}
          placeholder="Detalle de los recursos o del personal que podés enviar…"
          aria-invalid={!!fieldErrors.observaciones}
          aria-describedby={fieldErrors.observaciones ? 'observaciones-error' : undefined}
        />
        {fieldErrors.observaciones && <span id="observaciones-error" className="field-error">{fieldErrors.observaciones}</span>}
      </div>

      <div className="form-actions">
        <button type="submit" disabled={submitting}>
          {submitting ? 'Registrando…' : 'Registrar oferta'}
        </button>
      </div>
    </form>
  );
}
