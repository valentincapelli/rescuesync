package com.grupo21.sistemanacional.dto.recursos;

import io.swagger.v3.oas.annotations.media.Schema;
import com.grupo21.sistemanacional.model.TipoRecurso;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(description = "Recurso a agregar al inventario. Una fila por tipo de recurso y unidad.")
public record CrearRecursoRequest(

        @NotNull
        @Schema(description = "Tipo de recurso. Valores: PERSONAL_VOLUNTARIO, ALIMENTOS, AGUA_POTABLE, MEDICAMENTOS, ABRIGO_Y_ROPA, TRANSPORTE, MAQUINARIA_PESADA, REFUGIO_TEMPORAL, OTRO.", example = "ALIMENTOS") TipoRecurso tipoRecurso,

        /** Texto libre; se guarda en minúsculas y sin espacios de más ("KG " y "kg" son la misma unidad). */
        @NotBlank
        @Size(max = 30)
        @Schema(description = "Texto libre, hasta 30 caracteres. Se guarda en minúsculas y sin espacios de más.", example = "kg") String unidadMedida,

        @NotNull
        @Positive
        @Schema(description = "Stock total inicial. Mayor que 0.", example = "100") Integer cantidadTotal
) {}
