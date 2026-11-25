package calendario.core.modelo;

import java.time.LocalDate;

public record Calendario(Integer id, LocalDate fecha, Tipo tipo, String descripcion) {
    public Calendario {
        if ((id != null && id <= 0) || fecha == null || tipo == null
                || descripcion == null || descripcion.isBlank() || descripcion.length() > 20) {
            throw new ErrorNegocio(CodigoError.SOLICITUD_INVALIDA,
                    "Los datos del día del calendario no son válidos.");
        }
    }
}
