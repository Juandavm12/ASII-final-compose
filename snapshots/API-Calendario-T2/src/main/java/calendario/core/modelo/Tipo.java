package calendario.core.modelo;

public record Tipo(Integer id, String tipo) {
    public Tipo {
        validarId(id);
        if (tipo == null || tipo.trim().isEmpty() || tipo.trim().length() > 100) {
            throw new ErrorNegocio(CodigoError.SOLICITUD_INVALIDA,
                    "El nombre del tipo debe contener entre 1 y 100 caracteres.");
        }
        tipo = tipo.trim();
    }

    public static void validarId(Integer id) {
        if (id == null || id <= 0) {
            throw new ErrorNegocio(CodigoError.SOLICITUD_INVALIDA,
                    "El identificador del tipo debe ser positivo.");
        }
    }
}
