import { useCallback, useEffect, useRef, useState } from 'react';
import type { FiltroIndicadores } from '../../types/indicadores';
import { mensajeDeError } from './formato';

export type EstadoBloque<T> =
  | { tipo: 'cargando'; datos: T | null }
  | { tipo: 'error'; mensaje: string }
  | { tipo: 'ok'; datos: T };

export interface Bloque<T> {
  estado: EstadoBloque<T>;
  reintentar: () => void;
}

/**
 * Pide un endpoint del dashboard para el filtro aplicado. Cada bloque tiene su propio estado,
 * así un endpoint que falla no afecta a los demás. Las respuestas viejas (de un filtro anterior
 * o de un reintento superado) se descartan.
 */
export function useBloque<T>(
  pedir: (filtro: FiltroIndicadores) => Promise<T>,
  filtro: FiltroIndicadores,
  mensajeFallback: string,
): Bloque<T> {
  const [estado, setEstado] = useState<EstadoBloque<T>>({ tipo: 'cargando', datos: null });
  const [intento, setIntento] = useState(0);
  const ultimoPedido = useRef(0);

  useEffect(() => {
    const pedido = ++ultimoPedido.current;
    // Mientras carga se conservan los datos anteriores para no hacer parpadear el bloque.
    setEstado((prev) => ({ tipo: 'cargando', datos: prev.tipo === 'ok' ? prev.datos : null }));
    pedir(filtro)
      .then((datos) => {
        if (pedido === ultimoPedido.current) setEstado({ tipo: 'ok', datos });
      })
      .catch((err: unknown) => {
        if (pedido === ultimoPedido.current) {
          setEstado({ tipo: 'error', mensaje: mensajeDeError(err, mensajeFallback) });
        }
      });
  }, [pedir, filtro, intento, mensajeFallback]);

  const reintentar = useCallback(() => setIntento((n) => n + 1), []);

  return { estado, reintentar };
}
