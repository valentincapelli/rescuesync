import { apiGet } from './client';

// Debe coincidir con com.grupo21.rescuesync.dto.InfoResponse del backend.
export interface InfoResponse {
  application: string;
  version: string;
  status: string;
  serverTime: string;
}

export function fetchInfo(): Promise<InfoResponse> {
  return apiGet<InfoResponse>('/info');
}
