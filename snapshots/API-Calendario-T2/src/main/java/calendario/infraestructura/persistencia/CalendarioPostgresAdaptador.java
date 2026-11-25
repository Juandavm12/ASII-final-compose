package calendario.infraestructura.persistencia;

import calendario.core.modelo.Calendario;
import calendario.core.puertos.CalendarioRepositorio;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TransactionRequiredException;
import java.time.LocalDate;
import java.util.List;

public class CalendarioPostgresAdaptador implements CalendarioRepositorio {
    // Dos enteros evitan colisiones con el espacio de advisory locks de una sola clave.
    private static final int ESPACIO_CALENDARIO = 0x43414C45;
    private final EntityManager em;
    private final CalendarioJpaRepositorio repositorio;

    CalendarioPostgresAdaptador(EntityManager em, CalendarioJpaRepositorio repositorio) {
        this.em = em;
        this.repositorio = repositorio;
    }

    @Override
    public List<Calendario> listarAnio(int anio) {
        return repositorio.listarRango(LocalDate.of(anio, 1, 1), LocalDate.of(anio, 12, 31))
                .stream().map(CalendarioEntidad::modelo).toList();
    }

    @Override
    public void reemplazarAnio(int anio, List<Calendario> dias) {
        exigirTransaccion();
        em.createQuery("delete from CalendarioEntidad c where c.fecha between :inicio and :fin")
                .setParameter("inicio", LocalDate.of(anio, 1, 1))
                .setParameter("fin", LocalDate.of(anio, 12, 31)).executeUpdate();
        em.clear();
        for (Calendario dia : dias) {
            if (dia.fecha().getYear() != anio) {
                throw new IllegalArgumentException("La fecha no pertenece al anio solicitado.");
            }
            var tipo = em.getReference(TipoEntidad.class, dia.tipo().id());
            em.persist(new CalendarioEntidad(dia, tipo));
        }
        em.flush();
    }

    @Override
    public void bloquearAnio(int anio) {
        exigirTransaccion();
        em.createNativeQuery("select pg_advisory_xact_lock(cast(:espacio as integer), cast(:anio as integer))")
                .setParameter("espacio", ESPACIO_CALENDARIO)
                .setParameter("anio", anio).getSingleResult();
    }

    private void exigirTransaccion() {
        if (!em.isJoinedToTransaction()) {
            throw new TransactionRequiredException("La operacion requiere una unidad de trabajo.");
        }
    }
}
