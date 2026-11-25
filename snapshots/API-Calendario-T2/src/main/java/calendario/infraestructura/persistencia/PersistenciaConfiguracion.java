package calendario.infraestructura.persistencia;

import calendario.core.puertos.CalendarioRepositorio;
import calendario.core.puertos.TipoRepositorio;
import calendario.core.puertos.UnidadDeTrabajo;
import jakarta.persistence.EntityManager;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
@EntityScan(basePackageClasses = {TipoEntidad.class, CalendarioEntidad.class})
@EnableJpaRepositories(basePackageClasses = TipoJpaRepositorio.class)
public class PersistenciaConfiguracion {
    @Bean
    TipoRepositorio tipoRepositorio(EntityManager em, TipoJpaRepositorio repositorio,
                                    PlatformTransactionManager gestor) {
        return new TipoPostgresAdaptador(em, repositorio, new TransactionTemplate(gestor));
    }

    @Bean
    CalendarioRepositorio calendarioRepositorio(EntityManager em, CalendarioJpaRepositorio repositorio) {
        return new CalendarioPostgresAdaptador(em, repositorio);
    }

    @Bean
    UnidadDeTrabajo unidadDeTrabajo(PlatformTransactionManager gestor) {
        return new UnidadDeTrabajoSpring(new TransactionTemplate(gestor));
    }
}
