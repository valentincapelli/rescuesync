package com.grupo21.rescuesync.dto;

import com.grupo21.rescuesync.model.DecisionCobertura;
import jakarta.validation.constraints.NotNull;

public record DecidirCoberturaRequest(@NotNull DecisionCobertura decision) {
}
