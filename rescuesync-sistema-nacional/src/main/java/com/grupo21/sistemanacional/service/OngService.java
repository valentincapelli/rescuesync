package com.grupo21.sistemanacional.service;

import com.grupo21.sistemanacional.dto.ongs.OngResponse;
import com.grupo21.sistemanacional.dto.ongs.RegistroOngRequest;
import com.grupo21.sistemanacional.exception.BusinessException;
import com.grupo21.sistemanacional.exception.ResourceNotFoundException;
import com.grupo21.sistemanacional.model.EstadoHabilitacion;
import com.grupo21.sistemanacional.model.Ong;
import com.grupo21.sistemanacional.model.Rol;
import com.grupo21.sistemanacional.model.Usuario;
import com.grupo21.sistemanacional.repository.OngRepository;
import com.grupo21.sistemanacional.repository.UsuarioRepository;
import com.grupo21.sistemanacional.security.AccesoOng;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OngService {

    private final OngRepository ongRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccesoOng accesoOng;

    /**
     * Onboarding: crea la ONG (ya HABILITADA) y su usuario representante en una sola transacción.
     * Si algo falla, no queda ni la ONG ni el usuario.
     */
    @Transactional
    public OngResponse registrar(RegistroOngRequest request) {
        String username = request.username().trim();

        if (ongRepository.existsByCuit(request.cuit())) {
            throw new BusinessException("Ya existe una ONG registrada con el CUIT " + request.cuit());
        }
        if (usuarioRepository.existsByUsername(username)) {
            throw new BusinessException("El nombre de usuario '" + username + "' ya está en uso");
        }

        Ong ong = new Ong(request.cuit(), request.razonSocial().trim());
        ong.setEstadoHabilitacion(EstadoHabilitacion.HABILITADA);
        ong = ongRepository.save(ong);

        usuarioRepository.save(new Usuario(
                username, passwordEncoder.encode(request.password()), Rol.ONG, ong));

        return OngResponse.from(ong);
    }

    /** Catálogo: ONGs habilitadas, visible para cualquier usuario autenticado. */
    @Transactional(readOnly = true)
    public List<OngResponse> listarHabilitadas() {
        return ongRepository.findByEstadoHabilitacionOrderByRazonSocial(EstadoHabilitacion.HABILITADA)
                .stream().map(OngResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public OngResponse obtener(Long id) {
        accesoOng.exigirLectura(id);
        return ongRepository.findById(id)
                .map(OngResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("ONG", id));
    }
}
