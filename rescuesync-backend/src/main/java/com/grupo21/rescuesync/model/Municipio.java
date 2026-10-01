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
@Table(name = "municipios")
public class Municipio extends BaseEntity {

    @Column(nullable = false, unique = true, length = 100)
    private String nombre;
}