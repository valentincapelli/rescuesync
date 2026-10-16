package com.grupo21.sistemanacional.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * ONG registrada en el Sistema Nacional. Se identifica entre sistemas por su CUIT
 * (RescueSync manda el CUIT en el JSON consolidado, no el id interno de esta base).
 */
@Getter
@Setter
@NoArgsConstructor
@ToString
@Entity
@Table(name = "ongs")
public class Ong extends BaseEntity {

    /** 11 dígitos, sin guiones. */
    @Column(nullable = false, unique = true, length = 11)
    private String cuit;

    @Column(name = "razon_social", nullable = false, length = 150)
    private String razonSocial;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_habilitacion", nullable = false, length = 20)
    private EstadoHabilitacion estadoHabilitacion = EstadoHabilitacion.PENDIENTE;

    public Ong(String cuit, String razonSocial) {
        this.cuit = cuit;
        this.razonSocial = razonSocial;
    }

    public boolean isHabilitada() {
        return estadoHabilitacion == EstadoHabilitacion.HABILITADA;
    }
}
