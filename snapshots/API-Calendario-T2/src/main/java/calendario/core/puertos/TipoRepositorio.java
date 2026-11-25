package calendario.core.puertos;

import calendario.core.modelo.Tipo;
import java.util.List;
import java.util.Optional;

public interface TipoRepositorio {
    Tipo crear(Tipo tipo);
    Optional<Tipo> obtener(Integer id);
    List<Tipo> listar();
    Tipo actualizar(Tipo tipo);
    void eliminar(Integer id);
    boolean estaEnUso(Integer id);
}
