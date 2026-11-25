package calendario.infraestructura.persistencia;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CalendarioJpaRepositorio extends JpaRepository<CalendarioEntidad, Integer> {
    @Query("select c from CalendarioEntidad c join fetch c.tipo "
            + "where c.fecha between :inicio and :fin order by c.fecha")
    List<CalendarioEntidad> listarRango(@Param("inicio") LocalDate inicio,
                                      @Param("fin") LocalDate fin);
}
