package calendario.infraestructura.persistencia;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TipoJpaRepositorio extends JpaRepository<TipoEntidad, Integer> { }
