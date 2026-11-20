package com.grupo21.rescuesync.service;

import com.grupo21.rescuesync.dto.ofertas.CrearOfertaRequest;
import com.grupo21.rescuesync.dto.ofertas.EditarOfertaRequest;
import com.grupo21.rescuesync.dto.ofertas.OfertaEvaluadaResponse;
import com.grupo21.rescuesync.dto.ofertas.OfertaResponse;
import com.grupo21.rescuesync.dto.ofertas.OfertasEvaluadasEmergenciaResponse;
import com.grupo21.rescuesync.dto.ofertas.OfertasEvaluadasEmergenciaResponse.LoteEvaluado;
import com.grupo21.rescuesync.dto.ofertas.SeleccionarOfertasRequest;
import com.grupo21.rescuesync.dto.ofertas.SeleccionarOfertasResponse;
import com.grupo21.rescuesync.exception.BusinessException;
import com.grupo21.rescuesync.exception.ResourceNotFoundException;
import com.grupo21.rescuesync.model.Emergencia;
import com.grupo21.rescuesync.model.EstadoEmergencia;
import com.grupo21.rescuesync.model.EstadoLote;
import com.grupo21.rescuesync.model.EstadoOferta;
import com.grupo21.rescuesync.model.Lote;
import com.grupo21.rescuesync.model.Oferta;
import com.grupo21.rescuesync.model.Ong;
import com.grupo21.rescuesync.model.TipoNotificacion;
import com.grupo21.rescuesync.model.Usuario;
import com.grupo21.rescuesync.repository.EmergenciaRepository;
import com.grupo21.rescuesync.repository.LoteRepository;
import com.grupo21.rescuesync.repository.OfertaRepository;
import com.grupo21.rescuesync.repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
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
    private final NotificacionService notificacionService;
    private final HistorialEstadoService historialEstadoService;

    /** Principal antes que apoyo; las evaluadas sin perfil (datos de demo) al final. */
    private static final Comparator<OfertaEvaluadaResponse> PRINCIPALES_PRIMERO = Comparator
            .comparing((OfertaEvaluadaResponse oferta) ->
                    oferta.validacion() == null ? Integer.MAX_VALUE : oferta.validacion().clasificacion().ordinal())
            .thenComparing(OfertaEvaluadaResponse::id);

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
        historialEstadoService.registrarAlta(guardada);

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
    
    @Transactional(readOnly = true)
     public List<OfertasEvaluadasEmergenciaResponse> obtenerOfertasEvaluadasDelMunicipio(
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

        Map<Long, Emergencia> emergencias = new TreeMap<>();
        Map<Long, Map<Long, Lote>> lotesPorEmergencia = new TreeMap<>();
        Map<Long, Map<Long, List<OfertaEvaluadaResponse>>> ofertasPorEmergencia = new TreeMap<>();

        for (Oferta oferta : ofertas) {
            Emergencia emergencia = oferta.getLote().getEmergencia();
            if (emergencia.getEstado() != EstadoEmergencia.COBERTURA_EVALUADA
                    && emergencia.getEstado() != EstadoEmergencia.COBERTURA_PARCIAL) {
                continue;
            }

            Long emergenciaId = emergencia.getId();
            Long loteId = oferta.getLote().getId();
            emergencias.put(emergenciaId, emergencia);
            lotesPorEmergencia
                    .computeIfAbsent(emergenciaId, id -> new TreeMap<>())
                    .put(loteId, oferta.getLote());
            ofertasPorEmergencia
                    .computeIfAbsent(emergenciaId, id -> new TreeMap<>())
                    .computeIfAbsent(loteId, id -> new ArrayList<>())
                    .add(OfertaEvaluadaResponse.de(oferta));
        }

        return emergencias.entrySet().stream()
                .map(entry -> {
                    Emergencia emergencia = entry.getValue();
                    Long emergenciaId = entry.getKey();
                    List<LoteEvaluado> lotes = lotesPorEmergencia.get(emergenciaId)
                            .entrySet()
                            .stream()
                            .map(loteEntry -> {
                                Lote lote = loteEntry.getValue();
                                List<OfertaEvaluadaResponse> delLote =
                                        ofertasPorEmergencia.get(emergenciaId).get(lote.getId());
                                return new LoteEvaluado(
                                        lote.getId(),
                                        lote.getTipoRecurso(),
                                        lote.getDescripcion(),
                                        lote.getCantidadRequerida(),
                                        lote.getUnidadMedida(),
                                        lote.getEstado(),
                                        delLote.stream()
                                                .filter(OfertaEvaluadaResponse::seleccionable)
                                                .sorted(PRINCIPALES_PRIMERO)
                                                .toList(),
                                        delLote.stream()
                                                .filter(oferta -> !oferta.seleccionable())
                                                .toList()
                                );
                            })
                            .toList();

                    return new OfertasEvaluadasEmergenciaResponse(
                            emergenciaId,
                            emergencia.getTipoDesastre(),
                            emergencia.getNivelGravedad(),
                            emergencia.getZonaAfectada(),
                            emergencia.getDescripcion(),
                            emergencia.getMunicipio().getNombre(),
                            emergencia.getEstado(),
                            lotes
                    );
                })
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

        // 2. Obtener emergencia y verificar que pertenezca al municipio
        Emergencia emergencia = emergenciaRepository
                .findByIdAndMunicipio_Id(
                        request.emergenciaId(),
                        municipioId
                )
                .orElseThrow(() ->
                        new BusinessException(
                                "La emergencia no existe o no pertenece a su municipio"
                        )
                );

        if (emergencia.getEstado() != EstadoEmergencia.COBERTURA_EVALUADA
                && emergencia.getEstado() != EstadoEmergencia.COBERTURA_PARCIAL) {
            throw new BusinessException(
                    "La emergencia no está disponible para seleccionar ofertas"
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

        // 5b. Solo se adjudican ofertas validadas: una sin habilitación nacional no se puede seleccionar
        boolean seleccionaSinHabilitacion = ofertasEvaluadas.stream()
                .filter(oferta -> idsSeleccionados.contains(oferta.getId()))
                .anyMatch(oferta -> oferta.getValidacion() != null
                        && oferta.getValidacion().getClasificacion() != null
                        && !oferta.getValidacion().getClasificacion().esSeleccionable());
        if (seleccionaSinHabilitacion) {
            throw new BusinessException(
                    "Una o más ofertas no tienen habilitación del Sistema Nacional para el tipo de recurso"
            );
        }

        // 6. Actualizar estados de ofertas y lotes
        int seleccionadas = 0;
        int rechazadas = 0;

        Set<Long> lotesCerrados = new HashSet<>();

        for (Oferta oferta : ofertasEvaluadas) {

            if (idsSeleccionados.contains(oferta.getId())) {

                historialEstadoService.cambiarEstado(oferta, EstadoOferta.SELECCIONADA);
                seleccionadas++;

                // Cerrar el lote de la oferta seleccionada
                Lote lote = oferta.getLote();

                if (lotesCerrados.add(lote.getId())) {
                    historialEstadoService.cambiarEstado(lote, EstadoLote.CERRADO);
                }

            } else {
                historialEstadoService.cambiarEstado(oferta, EstadoOferta.RECHAZADA);
                rechazadas++;
            }
        }

        // 7. Guardar ofertas
        ofertaRepository.saveAll(ofertasEvaluadas);

        // 8. Cambiar estado de la emergencia
        historialEstadoService.cambiarEstado(emergencia, EstadoEmergencia.ADJUDICADA);

        // 9. Crear notificaciones para las ONGs
        crearNotificacionesPorResultado(ofertasEvaluadas, idsSeleccionados);

        // 10. Respuesta
        return new SeleccionarOfertasResponse(
                seleccionadas,
                rechazadas
        );
    }

    private void crearNotificacionesPorResultado(
            List<Oferta> ofertasEvaluadas,
            Set<Long> idsSeleccionados
    ) {

        for (Oferta oferta : ofertasEvaluadas) {

            boolean seleccionada = idsSeleccionados.contains(oferta.getId());

            Ong ong = oferta.getOng();
            Lote lote = oferta.getLote();
            Emergencia emergencia = lote.getEmergencia();

            String titulo;
            String mensaje;
            TipoNotificacion tipo;

            if (seleccionada) {

                titulo = "Oferta seleccionada";
                tipo = TipoNotificacion.OFERTA_SELECCIONADA;
            } else {

                titulo = "Oferta rechazada";
                tipo = TipoNotificacion.OFERTA_RECHAZADA;
            }

            String resultado = seleccionada
                    ? "fue seleccionada"
                    : "no fue seleccionada";
            mensaje = String.format(
                    "Tu oferta #%d %s. Municipio: %s. Emergencia #%d, zona: %s. " +
                    "Lote #%d: %s — %s (requerido: %d %s). " +
                    "Tu aporte: %d %s.",
                    oferta.getId(),
                    resultado,
                    emergencia.getMunicipio().getNombre(),
                    emergencia.getId(),
                    emergencia.getZonaAfectada(),
                    lote.getId(),
                    nombreLegible(lote.getTipoRecurso().name()),
                    lote.getDescripcion(),
                    lote.getCantidadRequerida(),
                    lote.getUnidadMedida(),
                    oferta.getCantidadOfrecida(),
                    lote.getUnidadMedida()
            );

            notificacionService.crearNotificacion(
                    ong,
                    titulo,
                    mensaje,
                    tipo
            );
        }
    }

    private String nombreLegible(String valor) {
        String nombre = valor.toLowerCase(Locale.ROOT).replace('_', ' ');
        return nombre.substring(0, 1).toUpperCase(Locale.ROOT) + nombre.substring(1);
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
