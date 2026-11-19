package com.grupo21.rescuesync.dto.despliegues;

import com.grupo21.rescuesync.model.TipoNovedadDespliegue;
import java.time.Instant;

public record NovedadDespliegueResponse(Long id, TipoNovedadDespliegue tipo, String observacion,
        String autor, Instant fecha, boolean confirmada) {
}
