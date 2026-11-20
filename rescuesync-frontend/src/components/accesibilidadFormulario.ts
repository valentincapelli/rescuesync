/**
 * Ayudantes de accesibilidad para formularios con validación propia.
 * El mensaje de error de cada campo debe tener id `${idCampo}-error`.
 */

/** Atributos que asocian un campo con su mensaje de error para los lectores de pantalla. */
export function propsCampo(idCampo: string, error?: string | null) {
  return error
    ? ({ 'aria-invalid': true, 'aria-describedby': `${idCampo}-error` } as const)
    : ({} as const);
}

/**
 * Lleva el foco al primer campo con error, en el orden del formulario. Se difiere un instante para que el
 * campo ya esté renderizado con sus atributos nuevos y el lector anuncie también el mensaje.
 */
export function enfocarPrimerError(
  idsEnOrden: readonly string[],
  errores: Partial<Record<string, string | undefined>>,
): void {
  const id = idsEnOrden.find((campo) => errores[campo]);
  if (!id) return;
  window.setTimeout(() => document.getElementById(id)?.focus(), 0);
}
