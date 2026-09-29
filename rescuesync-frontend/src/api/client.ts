// Cliente HTTP mínimo hacia el backend de RescueSync.
// Base URL configurable con VITE_API_URL (ver .env.example); por defecto
// apunta al backend local en :8081 (ver rescuesync-backend/README.md).
export const API_URL = import.meta.env.VITE_API_URL ?? 'http://localhost:8081/api';

export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status: number,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

export async function apiGet<T>(path: string): Promise<T> {
  const res = await fetch(`${API_URL}${path}`);
  if (!res.ok) {
    throw new ApiError(`GET ${path} devolvió ${res.status}`, res.status);
  }
  return (await res.json()) as T;
}
