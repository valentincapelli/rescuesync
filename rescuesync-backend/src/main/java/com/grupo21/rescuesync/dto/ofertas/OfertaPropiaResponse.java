package com.grupo21.rescuesync.dto.ofertas;

import com.grupo21.rescuesync.dto.LoteResponse;

/** Datos necesarios para consultar y editar los ofrecimientos de la ONG autenticada. */
public record OfertaPropiaResponse(
        OfertaResponse oferta,
        LoteResponse lote,
        Long emergenciaId,
        boolean editable
) {}
