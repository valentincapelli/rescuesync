import { useState } from 'react';
import type { FiltroIndicadores, FormatoReporte } from '../../types/indicadores';
import { mensajeDeError } from './formato';
import { fuenteIndicadores } from './fuenteDatos';

const FORMATOS: { formato: FormatoReporte; etiqueta: string; extension: string }[] = [
  { formato: 'json', etiqueta: 'JSON', extension: 'json' },
  { formato: 'csv', etiqueta: 'CSV', extension: 'csv' },
  { formato: 'xlsx', etiqueta: 'Excel', extension: 'xlsx' },
];

interface Props {
  filtro: FiltroIndicadores;
  /** Rango efectivo para el nombre del archivo (el que aplicó el backend, si se conoce). */
  desde: string;
  hasta: string;
}

function guardarBlob(blob: Blob, nombre: string) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = nombre;
  document.body.appendChild(a);
  a.click();
  a.remove();
  // Se libera después de que el navegador tomó la descarga.
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

/** Botones de exportación del reporte con los filtros aplicados. */
export function ExportarReporte({ filtro, desde, hasta }: Props) {
  const [descargando, setDescargando] = useState<FormatoReporte | null>(null);
  const [mensaje, setMensaje] = useState<{ tipo: 'error' | 'info'; texto: string } | null>(null);

  async function exportar(formato: FormatoReporte, etiqueta: string, extension: string) {
    setDescargando(formato);
    setMensaje(null);
    try {
      const blob = await fuenteIndicadores.descargar(formato, filtro);
      guardarBlob(blob, `indicadores_${desde}_${hasta}.${extension}`);
    } catch (err) {
      setMensaje({ tipo: 'error', texto: mensajeDeError(err, `No se pudo descargar el reporte (${etiqueta}).`) });
    } finally {
      setDescargando(null);
    }
  }

  return (
    <div className="ind-exportar">
      <span className="muted">Exportar:</span>
      {FORMATOS.map(({ formato, etiqueta, extension }) => (
        <button
          key={formato}
          type="button"
          className="secondary"
          onClick={() => void exportar(formato, etiqueta, extension)}
          disabled={descargando !== null}
        >
          {descargando === formato ? 'Descargando…' : etiqueta}
        </button>
      ))}
      {mensaje && (
        <span className={mensaje.tipo === 'error' ? 'ind-exportar-msg error' : 'ind-exportar-msg'} role="status">
          {mensaje.texto}
        </span>
      )}
    </div>
  );
}
