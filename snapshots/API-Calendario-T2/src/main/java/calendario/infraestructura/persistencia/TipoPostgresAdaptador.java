package calendario.infraestructura.persistencia;

import calendario.core.modelo.CodigoError;
import calendario.core.modelo.ErrorNegocio;
import calendario.core.modelo.Tipo;
import calendario.core.puertos.TipoRepositorio;
import jakarta.persistence.EntityManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.support.TransactionTemplate;

public class TipoPostgresAdaptador implements TipoRepositorio {
    private final EntityManager em;
    private final TipoJpaRepositorio repositorio;
    private final TransactionTemplate transacciones;

    TipoPostgresAdaptador(EntityManager em, TipoJpaRepositorio repositorio,
                          TransactionTemplate transacciones) {
        this.em = em;
        this.repositorio = repositorio;
        this.transacciones = transacciones;
    }

    @Override
    public Tipo crear(Tipo tipo) {
        try {
            return transacciones.execute(status -> {
                em.persist(new TipoEntidad(tipo));
                em.flush();
                return tipo;
            });
        } catch (RuntimeException error) {
            if (tieneEstado(error, "23505")) {
                throw new ErrorNegocio(CodigoError.TIPO_EXISTENTE, "El tipo ya existe.");
            }
            throw error;
        }
    }

    @Override
    public Optional<Tipo> obtener(Integer id) {
        return repositorio.findById(id).map(TipoEntidad::modelo);
    }

    @Override
    public List<Tipo> listar() {
        return repositorio.findAll(Sort.by("id")).stream().map(TipoEntidad::modelo).toList();
    }

    @Override
    public Tipo actualizar(Tipo tipo) {
        return transacciones.execute(status -> {
            int filas = em.createQuery("update TipoEntidad t set t.tipo = :nombre where t.id = :id")
                    .setParameter("nombre", tipo.tipo()).setParameter("id", tipo.id()).executeUpdate();
            em.clear();
            if (filas == 0) throw noEncontrado();
            return tipo;
        });
    }

    @Override
    public void eliminar(Integer id) {
        try {
            transacciones.executeWithoutResult(status -> {
                int filas = em.createQuery("delete from TipoEntidad t where t.id = :id")
                        .setParameter("id", id).executeUpdate();
                em.clear();
                if (filas == 0) throw noEncontrado();
            });
        } catch (RuntimeException error) {
            if (tieneEstado(error, "23503")) {
                throw new ErrorNegocio(CodigoError.TIPO_EN_USO,
                        "El tipo tiene dias del calendario asociados.");
            }
            throw error;
        }
    }

    @Override
    public boolean estaEnUso(Integer id) {
        return em.createQuery("select count(c) from CalendarioEntidad c where c.tipo.id = :id", Long.class)
                .setParameter("id", id).getSingleResult() > 0;
    }

    private static ErrorNegocio noEncontrado() {
        return new ErrorNegocio(CodigoError.TIPO_NO_ENCONTRADO, "El tipo no existe.");
    }

    private static boolean tieneEstado(Throwable error, String estado) {
        for (Throwable actual = error; actual != null; actual = actual.getCause()) {
            if (actual instanceof SQLException sql && estado.equals(sql.getSQLState())) return true;
        }
        return false;
    }
}
