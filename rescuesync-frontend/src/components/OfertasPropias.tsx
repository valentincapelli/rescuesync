import { useEffect, useState } from 'react';
import { ApiError } from '../api/client';
import { editarOferta, listarOfertasPropias } from '../api/ofertas';
import { TIPO_RECURSO_LABELS } from '../types/lote';
import { ESTADO_OFERTA_LABELS, type CrearOfertaRequest, type OfertaPropia } from '../types/oferta';
import { OfertaForm } from './OfertaForm';
import { VersionesOferta } from './VersionesOferta';

export function OfertasPropias({ recarga }: { recarga: number }) {
  const [ofertas, setOfertas] = useState<OfertaPropia[]>([]);
  const [editando, setEditando] = useState<OfertaPropia | null>(null);
  const [cargando, setCargando] = useState(true);
  const [guardando, setGuardando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [aviso, setAviso] = useState<string | null>(null);
  const [version, setVersion] = useState(0);
  // Ofertas con el historial de versiones desplegado.
  const [conHistorial, setConHistorial] = useState<Set<number>>(new Set());

  function alternarHistorial(id: number) {
    setConHistorial((actual) => {
      const siguiente = new Set(actual);
      if (siguiente.has(id)) siguiente.delete(id); else siguiente.add(id);
      return siguiente;
    });
  }

  useEffect(() => {
    let cancelado = false;
    setCargando(true);
    setError(null);
    listarOfertasPropias()
      .then((data) => {
        if (cancelado) return;
        setOfertas(data);
        setEditando(null);
      })
      .catch((err: unknown) => {
        if (!cancelado) setError(err instanceof ApiError ? err.message : 'No se pudieron cargar tus ofertas.');
      })
      .finally(() => !cancelado && setCargando(false));
    return () => { cancelado = true; };
  }, [recarga, version]);

  async function guardar(request: CrearOfertaRequest) {
    if (!editando) throw new Error('Elegí una oferta para editar.');
    setGuardando(true);
    try {
      const oferta = await editarOferta(editando.lote.id, editando.oferta.id, request);
      setAviso(`Oferta #${oferta.id} actualizada.`);
      setEditando(null);
      setVersion((v) => v + 1);
      return oferta;
    } finally {
      setGuardando(false);
    }
  }

  return (
    <section className="ofertas-propias" aria-labelledby="ofertas-propias-titulo">
      <div className="form-actions">
        <h3 id="ofertas-propias-titulo">Mis ofertas</h3>
        <button type="button" className="secondary" disabled={cargando || guardando}
          onClick={() => setVersion((v) => v + 1)}>Actualizar</button>
      </div>
      <p className="muted">Podés modificar tus ofrecimientos mientras la convocatoria siga abierta.</p>
      {aviso && <p className="form-banner success" role="status">{aviso}</p>}
      {error && <p className="form-banner error" role="alert">{error}</p>}
      {cargando ? <p role="status">Cargando tus ofertas…</p> : !error && (
        ofertas.length === 0 ? <p className="form-banner info" role="status">Todavía no cargaste ofertas.</p> :
          <div className="ofertas-propias-lista">
            {ofertas.map((propia) => (
              <article key={propia.oferta.id} className="emergencia-resumen">
                <strong>Oferta #{propia.oferta.id} · {TIPO_RECURSO_LABELS[propia.lote.tipoRecurso]}</strong>
                <p>Emergencia #{propia.emergenciaId} · Lote #{propia.lote.id}: {propia.lote.descripcion}</p>
                <p>{propia.oferta.cantidadOfrecida.toLocaleString('es-AR')} {propia.lote.unidadMedida}
                  {' · '}{ESTADO_OFERTA_LABELS[propia.oferta.estado]}</p>
                {propia.oferta.observaciones && <p>{propia.oferta.observaciones}</p>}
                {propia.editable ? (
                  <button type="button" className="secondary" disabled={guardando}
                    onClick={() => { setEditando(propia); setAviso(null); }}>Editar oferta #{propia.oferta.id}</button>
                ) : <p className="muted">Esta oferta ya no admite cambios.</p>}
                <button type="button" className="secondary" aria-expanded={conHistorial.has(propia.oferta.id)}
                  onClick={() => alternarHistorial(propia.oferta.id)}>
                  {conHistorial.has(propia.oferta.id) ? 'Ocultar historial' : 'Ver historial de versiones'}
                </button>
                {conHistorial.has(propia.oferta.id) && (
                  <VersionesOferta loteId={propia.lote.id} ofertaId={propia.oferta.id}
                    unidad={propia.lote.unidadMedida} recarga={version} />
                )}
              </article>
            ))}
          </div>
      )}
      {editando && <OfertaForm key={editando.oferta.id} lote={editando.lote} oferta={editando.oferta}
        onSubmit={guardar} onCancel={() => setEditando(null)} />}
    </section>
  );
}
