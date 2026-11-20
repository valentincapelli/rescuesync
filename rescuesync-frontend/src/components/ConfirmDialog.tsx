import { useEffect, useRef } from 'react';
import './ConfirmDialog.css';

interface Props {
  titulo: string;
  mensaje: string;
  confirmarLabel?: string;
  cancelarLabel?: string;
  /** Acción irreversible (por ejemplo, borrar): el foco inicial va a Cancelar, para que Enter no la ejecute. */
  destructivo?: boolean;
  onConfirmar: () => void;
  onCancelar: () => void;
}

/**
 * Modal de confirmación propio, en reemplazo de window.confirm (diálogo nativo que bloquea
 * la pestaña y no se puede estilizar ni automatizar). Esc o clic afuera cancelan.
 */
export function ConfirmDialog({
  titulo,
  mensaje,
  confirmarLabel = 'Confirmar',
  cancelarLabel = 'Cancelar',
  destructivo = false,
  onConfirmar,
  onCancelar,
}: Props) {
  const confirmarRef = useRef<HTMLButtonElement>(null);
  const cancelarRef = useRef<HTMLButtonElement>(null);
  const dialogRef = useRef<HTMLDivElement>(null);
  const anteriorRef = useRef<HTMLElement | null>(null);

  // Al abrir se recuerda quién tenía el foco; al cerrar se le devuelve (si no, el foco se pierde en <body>).
  useEffect(() => {
    anteriorRef.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    (destructivo ? cancelarRef : confirmarRef).current?.focus();
    return () => {
      const anterior = anteriorRef.current;
      if (anterior && anterior.isConnected) anterior.focus();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    function onKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape') {
        onCancelar();
        return;
      }
      if (e.key !== 'Tab') return;
      // Trampa de foco: Tab y Shift+Tab circulan solo entre los botones del modal.
      const botones = dialogRef.current?.querySelectorAll<HTMLElement>('button:not([disabled])');
      if (!botones || botones.length === 0) return;
      const primero = botones[0];
      const ultimo = botones[botones.length - 1];
      const activo = document.activeElement;
      if (e.shiftKey && (activo === primero || !dialogRef.current?.contains(activo))) {
        e.preventDefault();
        ultimo.focus();
      } else if (!e.shiftKey && (activo === ultimo || !dialogRef.current?.contains(activo))) {
        e.preventDefault();
        primero.focus();
      }
    }
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, [onCancelar]);

  return (
    <div className="confirm-overlay" onClick={onCancelar}>
      <div
        ref={dialogRef}
        className="confirm-dialog"
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="confirm-titulo"
        aria-describedby="confirm-mensaje"
        onClick={(e) => e.stopPropagation()}
      >
        <h3 id="confirm-titulo">{titulo}</h3>
        <p id="confirm-mensaje">{mensaje}</p>
        <div className="form-actions">
          <button type="button" className="secondary" ref={cancelarRef} onClick={onCancelar}>
            {cancelarLabel}
          </button>
          <button type="button" ref={confirmarRef} onClick={onConfirmar}>
            {confirmarLabel}
          </button>
        </div>
      </div>
    </div>
  );
}
