package com.grupo21.sistemanacional.security;

import com.grupo21.sistemanacional.exception.ForbiddenException;
import com.grupo21.sistemanacional.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Reglas de "quién puede tocar los datos de qué ONG".
 * <ul>
 *   <li>Lectura: ADMIN y SERVICIO (Bonita) ven cualquier ONG; una ONG, solo la suya.</li>
 *   <li>Escritura sobre una ONG: solo su propio representante.</li>
 * </ul>
 * El rol "a nivel endpoint" se controla con @PreAuthorize; esto agrega el "es la tuya".
 */
@Component
@RequiredArgsConstructor
public class AccesoOng {

    private final UsuarioRepository usuarioRepository;

    public void exigirLectura(Long ongId) {
        if (tieneRol("ADMIN") || tieneRol("SERVICIO")) {
            return;
        }
        exigirPropietaria(ongId);
    }

    public void exigirPropietaria(Long ongId) {
        if (!tieneRol("ONG")) {
            throw new ForbiddenException("Esta operación es solo para representantes de ONG");
        }
        Long ongPropia = usuarioRepository.findOngIdByUsername(autenticacion().getName()).orElse(null);
        if (!ongId.equals(ongPropia)) {
            throw new ForbiddenException("Solo podés operar sobre los datos de tu propia ONG");
        }
    }

    private Authentication autenticacion() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new ForbiddenException("Se requiere autenticación");
        }
        return auth;
    }

    private boolean tieneRol(String rol) {
        return autenticacion().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_" + rol));
    }
}
