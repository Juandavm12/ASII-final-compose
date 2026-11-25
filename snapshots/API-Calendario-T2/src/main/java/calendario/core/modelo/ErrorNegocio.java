package calendario.core.modelo;

import java.util.Objects;

public class ErrorNegocio extends RuntimeException {
    private final CodigoError codigo;

    public ErrorNegocio(CodigoError codigo, String mensaje) {
        super(mensaje);
        this.codigo = Objects.requireNonNull(codigo, "codigo");
    }

    public CodigoError codigo() {
        return codigo;
    }
}
