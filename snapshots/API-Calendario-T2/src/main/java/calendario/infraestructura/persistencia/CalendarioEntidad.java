package calendario.infraestructura.persistencia;

import calendario.core.modelo.Calendario;
import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "calendario")
public class CalendarioEntidad {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true)
    private LocalDate fecha;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_tipo", nullable = false)
    private TipoEntidad tipo;

    @Column(nullable = false, length = 20)
    private String descripcion;

    protected CalendarioEntidad() { }

    CalendarioEntidad(Calendario modelo, TipoEntidad tipo) {
        this.fecha = modelo.fecha();
        this.tipo = tipo;
        this.descripcion = modelo.descripcion();
    }

    Calendario modelo() {
        return new Calendario(id, fecha, tipo.modelo(), descripcion);
    }
}
