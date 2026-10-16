package com.grupo21.sistemanacional.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.Check;

/**
 * Cuenta de acceso a la API. El JWT llevará el rol y, para rol ONG, el ong_id.
 */
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = {"ong", "passwordHash"})
@Entity
@Table(name = "usuarios")
@Check(name = "ck_usuarios_ong_obligatoria", constraints = "rol <> 'ONG' OR ong_id IS NOT NULL")
public class Usuario extends BaseEntity {

    @Column(nullable = false, unique = true, length = 100)
    private String username;

    /** BCrypt. */
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Rol rol;

    /** Obligatoria si rol = ONG; null para ADMIN y SERVICIO. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ong_id")
    private Ong ong;

    public Usuario(String username, String passwordHash, Rol rol, Ong ong) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.rol = rol;
        this.ong = ong;
    }
}
