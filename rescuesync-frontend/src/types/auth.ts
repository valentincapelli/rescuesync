export type Rol =
  | 'OPERADOR_MUNICIPAL'
  | 'CENTRO_COORDINADOR'
  | 'REPRESENTANTE_ONG';

export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  email: string;
  rol: Rol;
}

export type AuthUser = LoginResponse;