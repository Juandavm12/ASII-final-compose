package calendario.core.puertos;

import calendario.core.modelo.Calendario;
import java.util.List;

public interface CalendarioRepositorio {
    List<Calendario> listarAnio(int anio);
    void reemplazarAnio(int anio, List<Calendario> dias);
    void bloquearAnio(int anio);
}
