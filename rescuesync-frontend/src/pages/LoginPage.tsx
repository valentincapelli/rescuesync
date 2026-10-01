import { useState } from 'react';
import type { ChangeEvent, FormEvent } from 'react';

import type { LoginRequest } from '../types/auth';
import { ApiError } from '../api/client';
import './LoginPage.css';

interface LoginPageProps {
  onLogin: (request: LoginRequest) => Promise<void>;
}

interface FieldErrors {
  email?: string;
  password?: string;
}

function validate(form: LoginRequest): FieldErrors {
  const errors: FieldErrors = {};

  const email = form.email.trim();
  const password = form.password;

  if (!email) {
    errors.email = 'El email es obligatorio.';
  } else if (
    !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)
  ) {
    errors.email = 'Ingresá un email válido.';
  }

  if (!password) {
    errors.password = 'La contraseña es obligatoria.';
  }

  return errors;
}

export function LoginPage({ onLogin }: LoginPageProps) {
  const [form, setForm] = useState<LoginRequest>({
    email: '',
    password: '',
  });

  const [fieldErrors, setFieldErrors] =
    useState<FieldErrors>({});

  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  function handleChange(
    event: ChangeEvent<HTMLInputElement>,
  ) {
    const { name, value } = event.target;

    setForm((prev) => ({
      ...prev,
      [name]: value,
    }));

    // Limpia el error del campo mientras el usuario corrige.
    setFieldErrors((prev) => ({
      ...prev,
      [name]: undefined,
    }));

    setError('');
  }

  async function handleSubmit(
    event: FormEvent<HTMLFormElement>,
  ) {
    event.preventDefault();

    setError('');

    const errors = validate(form);

    setFieldErrors(errors);

    // No hacemos la petición si hay errores.
    if (Object.keys(errors).length > 0) {
      return;
    }

    setLoading(true);

    try {
      await onLogin({
        email: form.email.trim(),
        password: form.password,
      });
    } catch (error) {
      if (error instanceof ApiError) {
        setError(error.message);
      } else {
        setError('No se pudo iniciar sesión');
      }
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="login-page">
      <section className="login-content">
        <header className="login-header">
          <div className="login-logo">RS</div>

          <div>
            <h1>RescueSync</h1>
            <p>Coordinación de emergencias</p>
          </div>
        </header>

        <div className="login-divider" />

        <div className="login-intro">
          <span className="login-kicker">
            ACCESO AL SISTEMA
          </span>

          <h2>Iniciar sesión</h2>

          <p>
            Accedé a la plataforma para gestionar
            emergencias y recursos.
          </p>
        </div>

        <form
          className="login-form"
          onSubmit={handleSubmit}
          noValidate
        >
          <div
            className={`login-field ${
              fieldErrors.email ? 'has-error' : ''
            }`}
          >
            <label htmlFor="email">
              Email
            </label>

            <input
              id="email"
              name="email"
              type="email"
              value={form.email}
              onChange={handleChange}
              placeholder="tu@email.com"
              autoComplete="email"
              required
              disabled={loading}
              aria-invalid={!!fieldErrors.email}
              aria-describedby={
                fieldErrors.email
                  ? 'email-error'
                  : undefined
              }
            />

            {fieldErrors.email && (
              <span
                id="email-error"
                className="login-field-error"
              >
                {fieldErrors.email}
              </span>
            )}
          </div>

          <div
            className={`login-field ${
              fieldErrors.password ? 'has-error' : ''
            }`}
          >
            <label htmlFor="password">
              Contraseña
            </label>

            <input
              id="password"
              name="password"
              type="password"
              value={form.password}
              onChange={handleChange}
              placeholder="••••••••"
              autoComplete="current-password"
              required
              disabled={loading}
              aria-invalid={!!fieldErrors.password}
              aria-describedby={
                fieldErrors.password
                  ? 'password-error'
                  : undefined
              }
            />

            {fieldErrors.password && (
              <span
                id="password-error"
                className="login-field-error"
              >
                {fieldErrors.password}
              </span>
            )}
          </div>

          {error && (
            <div
              className="login-error"
              role="alert"
            >
              {error}
            </div>
          )}

          <button
            className="login-button"
            type="submit"
            disabled={loading}
          >
            {loading
              ? 'Ingresando...'
              : 'Iniciar sesión'}
          </button>
        </form>

        <p className="login-footer">
          RescueSync · DSSD 2026 · Grupo 21
        </p>
      </section>
    </main>
  );
}