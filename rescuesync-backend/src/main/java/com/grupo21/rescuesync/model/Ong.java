package com.grupo21.rescuesync.model;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
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
    
    @Column(nullable = false, unique = true, length = 150)
    private String nombre;

    @OneToMany(mappedBy = "ong")
    private List<Notificacion> notificaciones = new ArrayList<>();
}