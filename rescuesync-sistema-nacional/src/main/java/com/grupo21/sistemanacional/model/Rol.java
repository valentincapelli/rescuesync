package com.grupo21.sistemanacional.model;

/**
 * Roles de la API del Sistema Nacional (distintos de los de RescueSync).
 * <ul>
 *   <li>ADMIN: habilita ONGs y consulta todo.</li>
 *   <li>ONG: gestiona su propio inventario y certificaciones.</li>
 *   <li>SERVICIO: usuario técnico con el que se autentica Bonita.</li>
 * </ul>
 */
public enum Rol {
    ADMIN,
    ONG,
    SERVICIO
}
