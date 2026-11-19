package com.grupo21.rescuesync.model;

import java.util.Set;

public enum Rol {

    OPERADOR_MUNICIPAL(
            Permiso.CONSULTAR_LOTES,
            Permiso.REGISTRAR_EMERGENCIA,
            Permiso.CONSULTAR_OFERTAS_VALIDADAS_MUNICIPIO,
            Permiso.SELECCIONAR_OFERTAS
    ),

    CENTRO_COORDINADOR(
            Permiso.CREAR_LOTE,
            Permiso.EDITAR_LOTE,
            Permiso.BORRAR_LOTE,
            Permiso.CONSULTAR_EMERGENCIAS,
            Permiso.CONSULTAR_LOTES,
            Permiso.CONSULTAR_OFERTAS,
            Permiso.PUBLICAR_CONVOCATORIA,
            Permiso.MONITOREAR_DESPLIEGUE
    ),

    REPRESENTANTE_ONG(
            Permiso.CREAR_OFERTA,
            Permiso.EDITAR_OFERTA,
            Permiso.CONSULTAR_OFERTAS,
            Permiso.CONSULTAR_LOTES,
            Permiso.CONSULTAR_EMERGENCIAS,
            Permiso.FINALIZAR_ACTIVIDAD
    ),
    SERVICIO_BONITA(
            Permiso.CONSULTAR_OFERTAS_CONSOLIDADAS
    ),
    DIRECTIVO(
            Permiso.CONSULTAR_INDICADORES
    );

    private final Set<Permiso> permisos;

    Rol(Permiso... permisos) {
        this.permisos = Set.of(permisos);
    }

    public Set<Permiso> getPermisos() {
        return permisos;
    }
}
