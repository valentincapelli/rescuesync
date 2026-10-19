package com.grupo21.sistemanacional.service;

import com.grupo21.sistemanacional.dto.recursos.ActualizarRecursoRequest;
import com.grupo21.sistemanacional.dto.recursos.CrearRecursoRequest;
import com.grupo21.sistemanacional.dto.recursos.RecursoResponse;
import com.grupo21.sistemanacional.exception.BusinessException;
import com.grupo21.sistemanacional.exception.ResourceNotFoundException;
import com.grupo21.sistemanacional.model.Ong;
import com.grupo21.sistemanacional.model.Recurso;
import com.grupo21.sistemanacional.model.TipoRecurso;
import com.grupo21.sistemanacional.repository.CompromisoRepository;
import com.grupo21.sistemanacional.repository.DisponibilidadView;
import com.grupo21.sistemanacional.repository.OngRepository;
import com.grupo21.sistemanacional.repository.RecursoRepository;
import com.grupo21.sistemanacional.security.AccesoOng;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * Inventario de las ONGs. Lo gestiona el representante de cada ONG sobre su propio stock.
 * <p>
 * Las modificaciones toman el lock de la fila (SELECT ... FOR UPDATE) para no pisarse con un
 * compromiso que se esté ejecutando al mismo tiempo sobre el mismo recurso.
 */
@Service
@RequiredArgsConstructor
public class RecursoService {

    private final RecursoRepository recursoRepository;
    private final OngRepository ongRepository;
    private final CompromisoRepository compromisoRepository;
    private final AccesoOng accesoOng;

    @Transactional
    public RecursoResponse crear(Long ongId, CrearRecursoRequest request) {
        accesoOng.exigirPropietaria(ongId);
        Ong ong = buscarOng(ongId);

        if (!ong.isHabilitada()) {
            throw new BusinessException("La ONG no está habilitada para gestionar recursos");
        }

        String unidad = normalizarUnidad(request.unidadMedida());
        if (recursoRepository.findByOngIdAndTipoRecursoAndUnidadMedida(ongId, request.tipoRecurso(), unidad).isPresent()) {
            throw new BusinessException("Ya tenés un recurso " + request.tipoRecurso() + " en '" + unidad
                    + "': actualizá su cantidad en lugar de crear otro");
        }

        Recurso recurso = recursoRepository.save(
                new Recurso(ong, request.tipoRecurso(), unidad, request.cantidadTotal()));
        return RecursoResponse.from(recurso);
    }

    @Transactional(readOnly = true)
    public List<RecursoResponse> listar(Long ongId) {
        accesoOng.exigirLectura(ongId);
        buscarOng(ongId);
        return recursoRepository.findByOngIdOrderByTipoRecurso(ongId).stream()
                .map(RecursoResponse::from)
                .toList();
    }

    @Transactional
    public RecursoResponse actualizar(Long ongId, Long recursoId, ActualizarRecursoRequest request) {
        accesoOng.exigirPropietaria(ongId);
        Recurso recurso = buscarBloqueado(ongId, recursoId);

        int nuevoTotal = request.cantidadTotal();
        if (nuevoTotal < recurso.getCantidadComprometida()) {
            throw new BusinessException("No se puede dejar el total en " + nuevoTotal + ": hay "
                    + recurso.getCantidadComprometida() + " unidades ya comprometidas");
        }
        recurso.setCantidadTotal(nuevoTotal);
        return RecursoResponse.from(recurso);
    }

    @Transactional
    public void eliminar(Long ongId, Long recursoId) {
        accesoOng.exigirPropietaria(ongId);
        Recurso recurso = buscarBloqueado(ongId, recursoId);

        if (recurso.getCantidadComprometida() > 0) {
            throw new BusinessException("No se puede eliminar: hay " + recurso.getCantidadComprometida()
                    + " unidades comprometidas en emergencias activas");
        }
        if (compromisoRepository.existsByRecursoId(recursoId)) {
            throw new BusinessException("No se puede eliminar: el recurso tiene historial de compromisos. "
                    + "Dejá su cantidad total en 0 en su lugar");
        }
        recursoRepository.delete(recurso);
    }

    /** Disponibilidad global (total - comprometido) de las ONGs habilitadas, opcionalmente de un solo tipo. */
    @Transactional(readOnly = true)
    public List<DisponibilidadView> disponibilidadGlobal(TipoRecurso tipo) {
        return recursoRepository.disponibilidadGlobal().stream()
                .filter(d -> tipo == null || d.tipoRecurso() == tipo)
                .toList();
    }

    private Ong buscarOng(Long ongId) {
        return ongRepository.findById(ongId)
                .orElseThrow(() -> new ResourceNotFoundException("ONG", ongId));
    }

    /** Recurso de esa ONG con la fila bloqueada. Si es de otra ONG responde 404, para no revelar que existe. */
    private Recurso buscarBloqueado(Long ongId, Long recursoId) {
        return recursoRepository.findByIdForUpdate(recursoId)
                .filter(r -> r.getOng().getId().equals(ongId))
                .orElseThrow(() -> new ResourceNotFoundException("Recurso", recursoId));
    }

    private static String normalizarUnidad(String unidad) {
        return unidad.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
