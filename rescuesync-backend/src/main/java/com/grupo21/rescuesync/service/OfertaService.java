package com.grupo21.rescuesync.service;

import com.grupo21.rescuesync.dto.ofertas.CrearOfertaRequest;
import com.grupo21.rescuesync.dto.ofertas.EditarOfertaRequest;
import com.grupo21.rescuesync.dto.ofertas.OfertaResponse;
import com.grupo21.rescuesync.dto.ofertas.SeleccionarOfertasRequest;
import com.grupo21.rescuesync.dto.ofertas.SeleccionarOfertasResponse;
import com.grupo21.rescuesync.exception.BusinessException;
import com.grupo21.rescuesync.exception.ResourceNotFoundException;
import com.grupo21.rescuesync.model.EstadoLote;
import com.grupo21.rescuesync.model.EstadoOferta;
import com.grupo21.rescuesync.model.Lote;
import com.grupo21.rescuesync.model.Oferta;
import com.grupo21.rescuesync.model.Ong;
import com.grupo21.rescuesync.model.Usuario;
import com.grupo21.rescuesync.repository.EmergenciaRepository;
import com.grupo21.rescuesync.repository.LoteRepository;
import com.grupo21.rescuesync.repository.OfertaRepository;
import com.grupo21.rescuesync.repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OfertaService {

    private final OfertaRepository ofertaRepository;
    private final LoteRepository loteRepository;
    private final UsuarioRepository usuarioRepository;
    private final EmergenciaRepository emergenciaRepository;

    @Transactional
    public OfertaResponse crear(
                Long loteId,
                CrearOfertaRequest request,
                String emailUsuario
        ) {
        Lote lote = loteRepository.findById(loteId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Lote con id " + loteId + " no encontrado"
                        )
                );

        validarLotePublicado(lote);

        Usuario usuario = usuarioRepository.findByEmail(emailUsuario)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Usuario no encontrado"
                        )
                );

        Ong ong = usuario.getOng();

        if (ong == null) {
                throw new BusinessException(
                        "El usuario no está asociado a una ONG"
                );
        }

        if (!ofertaRepository.findByOngIdAndLoteId(ong.getId(), loteId).isEmpty()) {
                throw new BusinessException(
                        "Ya existe una oferta de esta ONG para este lote"
                );
        }

        Oferta oferta = new Oferta();

        oferta.setLote(lote);
        oferta.setOng(ong);
        oferta.setCantidadOfrecida(request.cantidadOfrecida());
        oferta.setObservaciones(request.observaciones());
        oferta.setEstado(EstadoOferta.PENDIENTE);

        Oferta guardada = ofertaRepository.save(oferta);

        return toResponse(guardada);
        }

    @Transactional
    public OfertaResponse editar(
                Long ofertaId,
                EditarOfertaRequest request,
                String emailUsuario
        ) {

        Oferta oferta = ofertaRepository.findById(ofertaId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Oferta con id " + ofertaId + " no encontrada"
                        )
                );
        
        Usuario usuario = usuarioRepository.findByEmail(emailUsuario)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Usuario no encontrado"
                        )
                );

        Ong ong = usuario.getOng();

        if (ong == null) {
                throw new BusinessException(
                        "El usuario no está asociado a una ONG"
                );
        }
        

        if (!oferta.getOng().getId().equals(ong.getId())) {
                throw new BusinessException(
                        "La ONG no tiene permiso para editar esta oferta"
                );
        }

        validarLotePublicado(oferta.getLote());

        oferta.setCantidadOfrecida(request.cantidadOfrecida());
        oferta.setObservaciones(request.observaciones());

        Oferta actualizada = ofertaRepository.save(oferta);

        return toResponse(actualizada);
        }

    /** Solo se oferta sobre lotes de una convocatoria publicada (no en BORRADOR ni cerrados). */
        private void validarLotePublicado(Lote lote) {
        if (lote.getEstado() != EstadoLote.PUBLICADO) {
                throw new BusinessException(
                        "El lote " + lote.getId() + " está en estado " + lote.getEstado()
                                + ": no admite ofertas"
                );
        }

        if (lote.getEmergencia().getFechaCierreConvocatoria() == null) {
                throw new BusinessException(
                        "La convocatoria no tiene una fecha de cierre definida"
                );
        }

        if (!LocalDateTime.now().isBefore(
                lote.getEmergencia().getFechaCierreConvocatoria()
        )) {
                throw new BusinessException(
                        "El período de recepción de ofertas ha finalizado"
                );
        }
        }
    
    @Transactional
     public List<OfertaResponse> obtenerOfertasEvaluadasDelMunicipio(
            String email
    ) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Usuario no encontrado")
                );

        if (usuario.getMunicipio() == null) {
            throw new BusinessException(
                    "El usuario no tiene un municipio asociado"
            );
        }

        Long municipioId = usuario.getMunicipio().getId();

        List<Oferta> ofertas = ofertaRepository
                .findByLote_Emergencia_Municipio_IdAndEstado(
                        municipioId,
                        EstadoOferta.EVALUADA
                );

        return ofertas.stream()
                .map(oferta -> toResponse(oferta))
                .toList();
    }

    @Transactional
    public SeleccionarOfertasResponse seleccionarOfertas(
            String email,
            SeleccionarOfertasRequest request
    ) {

        // 1. Obtener usuario autenticado
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Usuario no encontrado")
                );

        if (usuario.getMunicipio() == null) {
            throw new BusinessException(
                    "El usuario no tiene un municipio asociado"
            );
        }

        Long municipioId = usuario.getMunicipio().getId();

        // 2. Verificar que la emergencia pertenezca al municipio
        boolean perteneceAlMunicipio =
                emergenciaRepository.existsByIdAndMunicipio_Id(
                        request.emergenciaId(),
                        municipioId
                );

        if (!perteneceAlMunicipio) {
            throw new BusinessException(
                    "La emergencia no existe o no pertenece a su municipio"
            );
        }

        // 3. Obtener ofertas evaluadas de esa emergencia
        List<Oferta> ofertasEvaluadas =
                ofertaRepository.buscarConBloqueoPorEmergenciaYMunicipioYEstado(
                        request.emergenciaId(),
                        municipioId,
                        EstadoOferta.EVALUADA
                );

        // 4. Obtener IDs de ofertas evaluadas
        Set<Long> idsEvaluados = ofertasEvaluadas.stream()
                .map(Oferta::getId)
                .collect(Collectors.toSet());

        Set<Long> idsSeleccionados = request.ofertaIds();

        // 5. Validar que todas las ofertas seleccionadas
        // pertenezcan a esta emergencia y estén evaluadas
        if (!idsEvaluados.containsAll(idsSeleccionados)) {
            throw new BusinessException(
                    "Una o más ofertas no pertenecen a esta emergencia o no están evaluadas"
            );
        }

        // 6. Actualizar estados
        int seleccionadas = 0;
        int rechazadas = 0;

        for (Oferta oferta : ofertasEvaluadas) {

            if (idsSeleccionados.contains(oferta.getId())) {
                oferta.setEstado(EstadoOferta.SELECCIONADA);
                seleccionadas++;
            } else {
                oferta.setEstado(EstadoOferta.RECHAZADA);
                rechazadas++;
            }
        }

        // 7. Guardar cambios
        ofertaRepository.saveAll(ofertasEvaluadas);

        return new SeleccionarOfertasResponse(
                seleccionadas,
                rechazadas
        );
    }

    private OfertaResponse toResponse(Oferta oferta) {
        return new OfertaResponse(
                oferta.getId(),
                oferta.getLote().getId(),
                oferta.getOng().getNombre(),
                oferta.getCantidadOfrecida(),
                oferta.getObservaciones(),
                oferta.getEstado()
        );
    }
}
