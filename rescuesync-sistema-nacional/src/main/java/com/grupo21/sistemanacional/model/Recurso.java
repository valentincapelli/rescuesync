package com.grupo21.sistemanacional.model;

import com.grupo21.sistemanacional.exception.BusinessException;
import com.grupo21.sistemanacional.exception.StockInsuficienteException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.Check;

/**
 * Inventario de una ONG: una fila por (ONG, tipo de recurso, unidad).
 * Es la fila que se bloquea con SELECT ... FOR UPDATE al comprometer o liberar
 * (ver RecursoRepository#findAllByIdForUpdate).
 * <p>
 * cantidadComprometida no tiene setter: solo cambia con {@link #comprometer} y {@link #liberar},
 * que se llaman con la fila ya bloqueada. El CHECK de la tabla es la red de seguridad.
 */
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = "ong")
@Entity
@Table(name = "recursos",
        uniqueConstraints = @UniqueConstraint(name = "uk_recursos_ong_tipo_unidad",
                columnNames = {"ong_id", "tipo_recurso", "unidad_medida"}))
@Check(name = "ck_recursos_stock",
        constraints = "cantidad_comprometida >= 0 AND cantidad_comprometida <= cantidad_total")
public class Recurso extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ong_id", nullable = false)
    private Ong ong;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_recurso", nullable = false, length = 30)
    private TipoRecurso tipoRecurso;

    // TODO E3-01 punto abierto: unificar unidades con RescueSync (texto libre hoy en los lotes).
    @Column(name = "unidad_medida", nullable = false, length = 30)
    private String unidadMedida;

    @Column(name = "cantidad_total", nullable = false)
    private Integer cantidadTotal;

    @Setter(AccessLevel.NONE)
    @Column(name = "cantidad_comprometida", nullable = false)
    private Integer cantidadComprometida = 0;

    public Recurso(Ong ong, TipoRecurso tipoRecurso, String unidadMedida, int cantidadTotal) {
        this.ong = ong;
        this.tipoRecurso = tipoRecurso;
        this.unidadMedida = unidadMedida;
        this.cantidadTotal = cantidadTotal;
    }

    /** Disponibilidad = total - comprometido. Se calcula, no se guarda. */
    public int getDisponible() {
        return cantidadTotal - cantidadComprometida;
    }

    /** Llamar solo con la fila bloqueada (dentro de la transacción del compromiso). */
    public void comprometer(int cantidad) {
        if (cantidad <= 0) {
            throw new BusinessException("La cantidad a comprometer debe ser mayor a 0");
        }
        if (cantidad > getDisponible()) {
            throw new StockInsuficienteException(getId(), cantidad, getDisponible());
        }
        cantidadComprometida += cantidad;
    }

    /** Llamar solo con la fila bloqueada (dentro de la transacción de la liberación). */
    public void liberar(int cantidad) {
        if (cantidad <= 0 || cantidad > cantidadComprometida) {
            throw new BusinessException("No se pueden liberar " + cantidad
                    + " unidades: hay " + cantidadComprometida + " comprometidas");
        }
        cantidadComprometida -= cantidad;
    }
}
