package calendario.aplicacion;

import calendario.core.modelo.CodigoError;
import calendario.core.modelo.ErrorNegocio;
import calendario.core.modelo.Tipo;
import calendario.core.puertos.TipoRepositorio;
import calendario.core.puertos.UnidadDeTrabajo;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class TipoServicio {
    private final TipoRepositorio repositorio;
    private final UnidadDeTrabajo unidadDeTrabajo;

    public TipoServicio(TipoRepositorio repositorio, UnidadDeTrabajo unidadDeTrabajo) {
        this.repositorio = Objects.requireNonNull(repositorio);
        this.unidadDeTrabajo = Objects.requireNonNull(unidadDeTrabajo);
    }

    public Tipo crear(Integer id, String tipo) {
        Tipo nuevo = new Tipo(id, tipo);
        return unidadDeTrabajo.ejecutar(() -> {
            if (repositorio.obtener(id).isPresent()) {
                throw new ErrorNegocio(CodigoError.TIPO_EXISTENTE,
                        "Ya existe un tipo con el identificador indicado.");
            }
            return repositorio.crear(nuevo);
        });
    }

    public Tipo obtener(Integer id) {
        Tipo.validarId(id);
        return repositorio.obtener(id).orElseThrow(() -> new ErrorNegocio(
                CodigoError.TIPO_NO_ENCONTRADO, "No existe el tipo solicitado."));
    }

    public List<Tipo> listar() {
        return repositorio.listar().stream().sorted(Comparator.comparing(Tipo::id)).toList();
    }

    public Tipo actualizar(Integer id, String tipo) {
        Tipo actualizado = new Tipo(id, tipo);
        return unidadDeTrabajo.ejecutar(() -> {
            obtener(id);
            return repositorio.actualizar(actualizado);
        });
    }

    public void eliminar(Integer id) {
        Tipo.validarId(id);
        unidadDeTrabajo.ejecutar(() -> {
            obtener(id);
            if (repositorio.estaEnUso(id)) {
                throw new ErrorNegocio(CodigoError.TIPO_EN_USO,
                        "El tipo tiene dias del calendario asociados.");
            }
            repositorio.eliminar(id);
            return null;
        });
    }
}
