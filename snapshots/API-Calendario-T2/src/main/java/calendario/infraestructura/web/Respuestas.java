package calendario.infraestructura.web;

import calendario.core.modelo.Calendario;
import calendario.core.modelo.Tipo;

import java.time.LocalDate;

/** Representaciones de salida independientes de las entidades JPA. */
public final class Respuestas {
    private Respuestas() { }

    public record TipoDto(Integer id, String tipo) {
        public static TipoDto desde(Tipo tipo) {
            return new TipoDto(tipo.id(), tipo.tipo());
        }
    }

    public record DiaDto(Integer id, LocalDate fecha, TipoDto tipo, String descripcion) {
        public static DiaDto desde(Calendario dia) {
            return new DiaDto(dia.id(), dia.fecha(), TipoDto.desde(dia.tipo()), dia.descripcion());
        }
    }

    public record ErrorDto(String codigo, String mensaje) { }
}
