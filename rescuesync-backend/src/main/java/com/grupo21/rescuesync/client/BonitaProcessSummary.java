package com.grupo21.rescuesync.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Subconjunto de campos que nos interesan de la respuesta de
 * GET /API/bpm/process de Bonita (trae muchos más: version, deploymentDate,
 * activationState, etc. — se ignoran con @JsonIgnoreProperties).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record BonitaProcessSummary(String id, String name, String version) {
}
