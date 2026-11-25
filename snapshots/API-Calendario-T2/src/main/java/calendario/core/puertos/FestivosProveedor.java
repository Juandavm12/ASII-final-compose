package calendario.core.puertos;

import java.time.LocalDate;
import java.util.Set;

public interface FestivosProveedor {
    Set<LocalDate> obtener(int anio);
}
