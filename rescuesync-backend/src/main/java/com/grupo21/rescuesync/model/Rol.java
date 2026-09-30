package com.grupo21.rescuesync.model;

import java.util.Set;

public enum Rol {

    OPERADOR_MUNICIPAL(
            Permiso.CREAR_LOTE,
            Permiso.EDITAR_LOTE,
            Permiso.CONSULTAR_LOTES
    ),

    CENTRO_COORDINADOR(
            Permiso.CONSULTAR_EMERGENCIAS,
            Permiso.CONSULTAR_LOTES,
            Permiso.CONSULTAR_OFERTAS
    ),

    REPRESENTANTE_ONG(
            Permiso.CREAR_OFERTA,
            Permiso.EDITAR_OFERTA,
            Permiso.CONSULTAR_OFERTAS
    );

    private final Set<Permiso> permisos;

    Rol(Permiso... permisos) {
        this.permisos = Set.of(permisos);
    }

    public Set<Permiso> getPermisos() {
        return permisos;
    }
}