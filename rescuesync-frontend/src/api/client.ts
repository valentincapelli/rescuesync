// Cliente HTTP mínimo hacia el backend de RescueSync.
// Base URL configurable con VITE_API_URL (ver .env.example); por defecto
// apunta al backend local en :8081 (ver rescuesync-backend/README.md).
export const API_URL = import.meta.env.VITE_API_URL ?? 'http://localhost:8081/api';

// Espeja com.grupo21.rescuesync.dto.ApiError del backend.
interface ApiErrorBody {
  message?: string;
  fieldErrors?: Record<string, string>;
}

export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status: number,
    public readonly fieldErrors?: Record<string, string>,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

async function toApiError(res: Response, fallback: string): Promise<ApiError> {
  try {
    const body = (await res.json()) as ApiErrorBody;
    return new ApiError(body.message ?? fallback, res.status, body.fieldErrors);
  } catch {
    return new ApiError(fallback, res.status);
  }
}

export async function apiGet<T>(path: string): Promise<T> {
  const res = await fetch(`${API_URL}${path}`);
  if (!res.ok) {
    throw await toApiError(res, `GET ${path} devolvió ${res.status}`);
  }
  return (await res.json()) as T;
}

export async function apiPost<T>(path: string, body: unknown): Promise<T> {
  const res = await fetch(`${API_URL}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  if (!res.ok) {
    throw await toApiError(res, `POST ${path} devolvió ${res.status}`);
  }
  return (await res.json()) as T;
}
