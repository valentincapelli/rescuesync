import { apiGet, apiPost } from './client';
import type { LoginRequest, LoginResponse } from '../types/auth';

export function login(
  request: LoginRequest,
): Promise<LoginResponse> {
  return apiPost<LoginResponse>('/auth/login', request);
}

export async function logout(): Promise<void> {
  await apiPost<void>('/auth/logout');
}

export function me(): Promise<LoginResponse> {
  return apiGet<LoginResponse>('/auth/me');
}