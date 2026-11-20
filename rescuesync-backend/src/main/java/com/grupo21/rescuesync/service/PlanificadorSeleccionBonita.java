package com.grupo21.rescuesync.service;

import com.grupo21.rescuesync.model.EstadoIntencionSeleccionBonita;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/** Calcula el contrato de la próxima selección a partir de las ofertas ya adjudicadas. */
@Component
public class PlanificadorSeleccionBonita {

    public record OfertaEnCola(long ofertaId, EstadoIntencionSeleccionBonita estado) {}
    public record ProximaSeleccion(long ofertaId, boolean continuarSeleccionandoInput) {}

    public Optional<ProximaSeleccion> siguiente(Collection<OfertaEnCola> adjudicadas) {
        Set<Long> ids = new HashSet<>();
        for (OfertaEnCola oferta : adjudicadas) {
            if (oferta == null || oferta.ofertaId() <= 0 || oferta.estado() == null
                    || !ids.add(oferta.ofertaId())) {
                throw new IllegalArgumentException("La cola de adjudicaciones contiene ofertas inválidas o duplicadas");
            }
        }

        // Bonita sólo vuelve a Seleccionar oferta después de Liberar recursos.
        if (adjudicadas.stream().anyMatch(o -> o.estado() != EstadoIntencionSeleccionBonita.PENDIENTE
                && o.estado() != EstadoIntencionSeleccionBonita.LIBERADA)) {
            return Optional.empty();
        }

        var pendientes = adjudicadas.stream()
                .filter(o -> o.estado() == EstadoIntencionSeleccionBonita.PENDIENTE)
                .sorted(Comparator.comparingLong(OfertaEnCola::ofertaId))
                .toList();
        if (pendientes.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ProximaSeleccion(pendientes.getFirst().ofertaId(), pendientes.size() > 1));
    }
}
