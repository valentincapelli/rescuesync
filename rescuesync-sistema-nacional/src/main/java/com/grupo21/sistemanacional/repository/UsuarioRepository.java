package com.grupo21.sistemanacional.repository;

import com.grupo21.sistemanacional.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByUsername(String username);

    boolean existsByUsername(String username);

    /** Id de la ONG del usuario; vacío para ADMIN y SERVICIO (no tienen ONG). */
    @Query("select u.ong.id from Usuario u where u.username = :username")
    Optional<Long> findOngIdByUsername(@Param("username") String username);
}
