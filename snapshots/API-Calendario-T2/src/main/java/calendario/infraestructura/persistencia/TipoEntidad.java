package calendario.infraestructura.persistencia;

import calendario.core.modelo.Tipo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "tipo")
public class TipoEntidad {
    @Id
    private Integer id;

    @Column(name = "tipo", nullable = false, length = 100)
    private String tipo;

    protected TipoEntidad() { }

    TipoEntidad(Tipo modelo) {
        this.id = modelo.id();
        this.tipo = modelo.tipo();
    }

    Tipo modelo() {
        return new Tipo(id, tipo);
    }
}
