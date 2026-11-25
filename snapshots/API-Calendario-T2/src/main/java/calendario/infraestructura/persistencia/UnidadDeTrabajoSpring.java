package calendario.infraestructura.persistencia;

import calendario.core.puertos.UnidadDeTrabajo;
import java.util.function.Supplier;
import org.springframework.transaction.support.TransactionTemplate;

public class UnidadDeTrabajoSpring implements UnidadDeTrabajo {
    private final TransactionTemplate transacciones;

    UnidadDeTrabajoSpring(TransactionTemplate transacciones) {
        this.transacciones = transacciones;
    }

    @Override
    public <T> T ejecutar(Supplier<T> trabajo) {
        return transacciones.execute(status -> trabajo.get());
    }
}
