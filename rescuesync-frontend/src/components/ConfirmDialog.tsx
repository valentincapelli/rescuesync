import { useEffect, useRef } from 'react';
import './ConfirmDialog.css';

interface Props {
  titulo: string;
  mensaje: string;
  confirmarLabel?: string;
  cancelarLabel?: string;
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
  onConfirmar,
  onCancelar,
}: Props) {
  const confirmarRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    confirmarRef.current?.focus();
    function onKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape') onCancelar();
    }
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, [onCancelar]);

  return (
    <div className="confirm-overlay" onClick={onCancelar}>
      <div
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
          <button type="button" className="secondary" onClick={onCancelar}>
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
