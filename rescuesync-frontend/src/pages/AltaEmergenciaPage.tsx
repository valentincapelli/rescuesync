import { useState } from 'react';
import type { FormEvent } from 'react';
import './AltaEmergenciaPage.css';

import { crearEmergencia } from '../api/emergencias';
import { ApiError } from '../api/client';

import {
  NIVEL_GRAVEDAD_LABELS,
  NIVELES_GRAVEDAD,
  TIPO_DESASTRE_LABELS,
  TIPOS_DESASTRE,
  type CrearEmergenciaRequest,
  type Emergencia,
} from '../types/emergencia';

const initialForm: CrearEmergenciaRequest = {
  tipoDesastre: TIPOS_DESASTRE[0],
  nivelGravedad: NIVELES_GRAVEDAD[0],
  zonaAfectada: '',
  descripcion: '',
};

type FieldErrors = Partial<
  Record<keyof CrearEmergenciaRequest, string>
>;

function validate(
  form: CrearEmergenciaRequest,
): FieldErrors {
  const errors: FieldErrors = {};

  if (!form.zonaAfectada.trim()) {
    errors.zonaAfectada = 'Indicá la zona afectada.';
  } else if (form.zonaAfectada.length > 200) {
    errors.zonaAfectada = 'Máximo 200 caracteres.';
  }

  if (!form.descripcion.trim()) {
    errors.descripcion =
      'Describí brevemente la situación.';
  }

  return errors;
}

export function AltaEmergenciaPage() {
  const [form, setForm] =
    useState<CrearEmergenciaRequest>(initialForm);

  const [fieldErrors, setFieldErrors] =
    useState<FieldErrors>({});

  const [submitting, setSubmitting] =
    useState(false);

  const [generalError, setGeneralError] =
    useState<string | null>(null);

  const [creada, setCreada] =
    useState<Emergencia | null>(null);

  function updateField<
    K extends keyof CrearEmergenciaRequest
  >(
    field: K,
    value: CrearEmergenciaRequest[K],
  ) {
    setForm((prev) => ({
      ...prev,
      [field]: value,
    }));
  }

  async function handleSubmit(
    e: FormEvent,
  ) {
    e.preventDefault();

    setGeneralError(null);
    setCreada(null);

    const errors = validate(form);

    setFieldErrors(errors);

    if (Object.keys(errors).length > 0) {
      return;
    }

    setSubmitting(true);

    try {
      const emergencia = await crearEmergencia(form);

      setCreada(emergencia);
      setForm(initialForm);
      setFieldErrors({});
    } catch (err) {
      if (
        err instanceof ApiError &&
        err.fieldErrors
      ) {
        setFieldErrors(
          err.fieldErrors as FieldErrors,
        );
      } else if (err instanceof ApiError) {
        setGeneralError(err.message);
      } else {
        setGeneralError(
          'No se pudo registrar la emergencia. Intentá de nuevo.',
        );
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <section>
      <h2>Registrar emergencia</h2>

      <p>
        Primer paso del proceso: el Municipio registra
        la emergencia indicando tipo de desastre, nivel
        de gravedad, zona afectada y una descripción
        inicial.
      </p>

      {creada && (
        <p className="form-banner success">
          Emergencia #{creada.id} registrada con estado{' '}
          {creada.estado}.
        </p>
      )}

      {generalError && (
        <p className="form-banner error">
          {generalError}
        </p>
      )}

      <form
        className="emergencia-form"
        onSubmit={handleSubmit}
        noValidate
      >
        <div className="form-row">
          <div
            className={`form-field ${
              fieldErrors.tipoDesastre
                ? 'has-error'
                : ''
            }`}
          >
            <label htmlFor="tipoDesastre">
              Tipo de desastre
            </label>

            <select
              id="tipoDesastre"
              value={form.tipoDesastre}
              onChange={(e) =>
                updateField(
                  'tipoDesastre',
                  e.target.value as CrearEmergenciaRequest['tipoDesastre'],
                )
              }
            >
              {TIPOS_DESASTRE.map((tipo) => (
                <option
                  key={tipo}
                  value={tipo}
                >
                  {TIPO_DESASTRE_LABELS[tipo]}
                </option>
              ))}
            </select>

            {fieldErrors.tipoDesastre && (
              <span className="field-error">
                {fieldErrors.tipoDesastre}
              </span>
            )}
          </div>

          <div
            className={`form-field ${
              fieldErrors.nivelGravedad
                ? 'has-error'
                : ''
            }`}
          >
            <label htmlFor="nivelGravedad">
              Nivel de gravedad
            </label>

            <select
              id="nivelGravedad"
              value={form.nivelGravedad}
              onChange={(e) =>
                updateField(
                  'nivelGravedad',
                  e.target.value as CrearEmergenciaRequest['nivelGravedad'],
                )
              }
            >
              {NIVELES_GRAVEDAD.map((nivel) => (
                <option
                  key={nivel}
                  value={nivel}
                >
                  {NIVEL_GRAVEDAD_LABELS[nivel]}
                </option>
              ))}
            </select>

            {fieldErrors.nivelGravedad && (
              <span className="field-error">
                {fieldErrors.nivelGravedad}
              </span>
            )}
          </div>
        </div>

        <div
          className={`form-field ${
            fieldErrors.zonaAfectada
              ? 'has-error'
              : ''
          }`}
        >
          <label htmlFor="zonaAfectada">
            Zona afectada
          </label>

          <input
            id="zonaAfectada"
            type="text"
            value={form.zonaAfectada}
            onChange={(e) =>
              updateField(
                'zonaAfectada',
                e.target.value,
              )
            }
            maxLength={200}
            placeholder="Ej: Barrio Los Hornos, cuenca del arroyo Maldonado"
          />

          {fieldErrors.zonaAfectada && (
            <span className="field-error">
              {fieldErrors.zonaAfectada}
            </span>
          )}
        </div>

        <div
          className={`form-field ${
            fieldErrors.descripcion
              ? 'has-error'
              : ''
          }`}
        >
          <label htmlFor="descripcion">
            Descripción inicial
          </label>

          <textarea
            id="descripcion"
            value={form.descripcion}
            onChange={(e) =>
              updateField(
                'descripcion',
                e.target.value,
              )
            }
            placeholder="Describí la situación: qué pasó, alcance estimado, urgencias detectadas…"
          />

          {fieldErrors.descripcion && (
            <span className="field-error">
              {fieldErrors.descripcion}
            </span>
          )}
        </div>

        <div className="form-actions">
          <button
            type="submit"
            disabled={submitting}
          >
            {submitting
              ? 'Registrando…'
              : 'Registrar emergencia'}
          </button>
        </div>
      </form>
    </section>
  );
}