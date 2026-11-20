package com.grupo21.sistemanacional.service;

import com.grupo21.sistemanacional.dto.certificaciones.ActualizarCertificacionRequest;
import com.grupo21.sistemanacional.dto.certificaciones.CapacidadHabilitadaResponse;
import com.grupo21.sistemanacional.dto.certificaciones.CertificacionResponse;
import com.grupo21.sistemanacional.dto.certificaciones.CrearCertificacionRequest;
import com.grupo21.sistemanacional.dto.certificaciones.ValidacionCertificacionResponse;
import com.grupo21.sistemanacional.dto.certificaciones.ValidarCertificacionRequest;
import com.grupo21.sistemanacional.exception.BusinessException;
import com.grupo21.sistemanacional.exception.ResourceNotFoundException;
import com.grupo21.sistemanacional.model.Certificacion;
import com.grupo21.sistemanacional.model.Ong;
import com.grupo21.sistemanacional.repository.CertificacionRepository;
import com.grupo21.sistemanacional.repository.OngRepository;
import com.grupo21.sistemanacional.security.AccesoOng;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Certificaciones de las ONGs. Las carga y modifica solo el ADMIN (el endpoint lo exige con @PreAuthorize);
 * la lectura la hace el propio representante de la ONG, el ADMIN o Bonita.
 */
@Service
@RequiredArgsConstructor
public class CertificacionService {

    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");

    private final CertificacionRepository certificacionRepository;
    private final OngRepository ongRepository;
    private final AccesoOng accesoOng;

    @Transactional
    public CertificacionResponse crear(Long ongId, CrearCertificacionRequest request) {
        Ong ong = buscarOng(ongId);

        if (certificacionRepository.existsByOngIdAndTipoRecurso(ongId, request.tipoRecurso())) {
            throw new BusinessException("La ONG ya tiene una certificación para " + request.tipoRecurso()
                    + ": actualizala en lugar de crear otra");
        }

        Certificacion certificacion = certificacionRepository.save(new Certificacion(
                ong, request.tipoRecurso(), request.nivelCompetencia(),
                request.nivelRiesgoMax(), request.fechaVencimiento()));
        return CertificacionResponse.from(certificacion, hoy());
    }

    @Transactional(readOnly = true)
    public List<CertificacionResponse> listar(Long ongId) {
        accesoOng.exigirLectura(ongId);
        buscarOng(ongId);
        LocalDate hoy = hoy();
        return certificacionRepository.findByOngIdOrderByTipoRecurso(ongId).stream()
                .map(c -> CertificacionResponse.from(c, hoy))
                .toList();
    }

    /** E3-05: informa capacidades certificadas, sin modificar ofertas ni reservar recursos. */
    @Transactional(readOnly = true)
    public ValidacionCertificacionResponse validar(ValidarCertificacionRequest request) {
        Ong ong = ongRepository.findByCuit(request.cuit()).orElse(null);
        if (ong == null) {
            return new ValidacionCertificacionResponse(request.cuit(), request.tipoRecurso(), null, List.of(),
                    "No hay una ONG registrada con el CUIT indicado");
        }
        if (!ong.isHabilitada()) {
            return new ValidacionCertificacionResponse(request.cuit(), request.tipoRecurso(),
                    ong.getEstadoHabilitacion(), List.of(), "La ONG no está habilitada en el Sistema Nacional");
        }

        List<CapacidadHabilitadaResponse> capacidades = certificacionRepository
                .findVigente(request.cuit(), request.tipoRecurso(), hoy()).stream()
                .map(CapacidadHabilitadaResponse::from)
                .toList();
        String observaciones = capacidades.isEmpty()
                ? "La ONG no tiene una certificación vigente para el tipo de recurso solicitado"
                : "Se informan el nivel de competencia y el riesgo máximo certificados para el tipo de recurso solicitado";
        return new ValidacionCertificacionResponse(request.cuit(), request.tipoRecurso(),
                ong.getEstadoHabilitacion(), capacidades, observaciones);
    }

    @Transactional
    public CertificacionResponse actualizar(Long ongId, Long certificacionId, ActualizarCertificacionRequest request) {
        Certificacion certificacion = buscarCertificacion(ongId, certificacionId);
        certificacion.setNivelCompetencia(request.nivelCompetencia());
        certificacion.setNivelRiesgoMax(request.nivelRiesgoMax());
        certificacion.setFechaVencimiento(request.fechaVencimiento());
        return CertificacionResponse.from(certificacion, hoy());
    }

    @Transactional
    public void eliminar(Long ongId, Long certificacionId) {
        certificacionRepository.delete(buscarCertificacion(ongId, certificacionId));
    }

    private Ong buscarOng(Long ongId) {
        return ongRepository.findById(ongId)
                .orElseThrow(() -> new ResourceNotFoundException("ONG", ongId));
    }

    private Certificacion buscarCertificacion(Long ongId, Long certificacionId) {
        return certificacionRepository.findByIdAndOngId(certificacionId, ongId)
                .orElseThrow(() -> new ResourceNotFoundException("Certificación", certificacionId));
    }

    private static LocalDate hoy() {
        return LocalDate.now(ZONA);
    }
}
