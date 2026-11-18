package com.grupo21.rescuesync.service;

import com.grupo21.rescuesync.dto.MunicipioResponse;
import com.grupo21.rescuesync.repository.MunicipioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MunicipioService {

    private final MunicipioRepository municipioRepository;

    /** Todos los municipios, ordenados por nombre. */
    @Transactional(readOnly = true)
    public List<MunicipioResponse> listar() {
        return municipioRepository.findAllByOrderByNombreAsc().stream()
                .map(m -> new MunicipioResponse(m.getId(), m.getNombre()))
                .toList();
    }
}
