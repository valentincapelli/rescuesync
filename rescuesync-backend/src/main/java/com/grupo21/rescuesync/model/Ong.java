package com.grupo21.rescuesync.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@ToString
@Entity
@Table(name = "ongs", uniqueConstraints = @UniqueConstraint(name = "uk_ongs_cuit", columnNames = "cuit"))
public class Ong extends BaseEntity {
    
    @Column(nullable = false, unique = true, length = 150)
    private String nombre;

    /** Identificación común con el Sistema Nacional. Completar las ONGs anteriores a E3-10. */
    @Pattern(regexp = "\\d{11}", message = "El CUIT debe tener 11 dígitos, sin guiones")
    @Column(length = 11)
    private String cuit;
}
