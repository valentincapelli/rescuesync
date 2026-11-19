package com.grupo21.rescuesync.client;

import java.util.Set;

/** Instantánea validada del protocolo E3-10; las ofertas procesadas ya fueron liberadas. */
public record EstadoDespliegueBonita(Long caseId, Long ofertaActualId, Long ofertaComprometidaId,
        Integer cantidadComprometida, Set<Long> ofertasLiberadas, boolean casoTerminado,
        String tareaMonitoreoId, String tareaFinalizacionId, Set<String> tareasCompletadas) {
}
