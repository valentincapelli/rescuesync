package com.grupo21.rescuesync.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@ToString
@Entity
@Table(name = "ongs")
public class Ong extends BaseEntity {
    
    // Para esta entrega solo hace falta el nombre
    @Column(nullable = false, unique = true, length = 150)
    private String nombre;
}