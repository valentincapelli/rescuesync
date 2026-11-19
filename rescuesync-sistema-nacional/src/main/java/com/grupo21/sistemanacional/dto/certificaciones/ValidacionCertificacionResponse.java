package com.grupo21.sistemanacional.dto.certificaciones;

import io.swagger.v3.oas.annotations.media.Schema;
import com.grupo21.sistemanacional.model.EstadoHabilitacion;
import com.grupo21.sistemanacional.model.TipoRecurso;

import java.util.List;

/**
 * Perfil de capacidades para el tipo consultado, sin aprobar ni rechazar una oferta.
 * Si la ONG no está registrada, estadoHabilitacion es null; si no hay capacidad habilitada, la lista está vacía.
 */
@Schema(description = "Perfil de capacidades de una ONG para el tipo consultado. No aprueba ni rechaza ofertas.")
public record ValidacionCertificacionResponse(
        @Schema(description = "CUIT de la ONG: 11 dígitos, sin guiones.", example = "30700000001") String cuit,
        @Schema(description = "Tipo de recurso. Valores: PERSONAL_VOLUNTARIO, ALIMENTOS, AGUA_POTABLE, MEDICAMENTOS, ABRIGO_Y_ROPA, TRANSPORTE, MAQUINARIA_PESADA, REFUGIO_TEMPORAL, OTRO.", example = "ALIMENTOS") TipoRecurso tipoRecurso,
        @Schema(description = "Estado de la ONG en el Sistema Nacional. Solo las HABILITADA pueden comprometer recursos. null si el CUIT no está registrado.", example = "HABILITADA") EstadoHabilitacion estadoHabilitacion,
        @Schema(description = "Capacidades vigentes para el tipo consultado. Lista vacía si no hay certificación vigente, la ONG no está habilitada o el CUIT no existe.") List<CapacidadHabilitadaResponse> capacidadesHabilitadas,
        @Schema(description = "Explica el resultado, en especial cuando la lista de capacidades está vacía.", example = "Se informan el nivel de competencia y el riesgo máximo certificados para el tipo de recurso solicitado") String observaciones
) {}
