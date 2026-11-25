package calendario.aplicacion;

import calendario.core.modelo.Calendario;
import calendario.core.modelo.CodigoError;
import calendario.core.modelo.ErrorNegocio;
import calendario.core.modelo.Tipo;
import calendario.core.puertos.CalendarioRepositorio;
import calendario.core.puertos.FestivosProveedor;
import calendario.core.puertos.TipoRepositorio;
import calendario.core.puertos.UnidadDeTrabajo;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class CalendarioServicio {
    private static final List<String> DIAS = List.of(
            "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo");
    private final CalendarioRepositorio calendarioRepositorio;
    private final TipoRepositorio tipoRepositorio;
    private final FestivosProveedor festivosProveedor;
    private final UnidadDeTrabajo unidadDeTrabajo;

    public CalendarioServicio(CalendarioRepositorio calendarioRepositorio,
                              TipoRepositorio tipoRepositorio,
                              FestivosProveedor festivosProveedor,
                              UnidadDeTrabajo unidadDeTrabajo) {
        this.calendarioRepositorio = Objects.requireNonNull(calendarioRepositorio);
        this.tipoRepositorio = Objects.requireNonNull(tipoRepositorio);
        this.festivosProveedor = Objects.requireNonNull(festivosProveedor);
        this.unidadDeTrabajo = Objects.requireNonNull(unidadDeTrabajo);
    }

    public boolean generar(int anio) {
        validarAnio(anio);
        Set<LocalDate> respuesta = festivosProveedor.obtener(anio);
        if (respuesta == null || respuesta.stream().anyMatch(
                fecha -> fecha == null || fecha.getYear() != anio)) {
            throw new ErrorNegocio(CodigoError.RESPUESTA_FESTIVOS_INVALIDA,
                    "La respuesta de festivos contiene fechas inválidas para el año solicitado.");
        }
        Set<LocalDate> festivos = Set.copyOf(respuesta);
        LocalDate inicio = LocalDate.of(anio, 1, 1);
        int cantidad = inicio.lengthOfYear();
        List<DiaPreparado> preparados = new ArrayList<>(cantidad);
        // El límite es una cantidad de días; nunca avanzamos después del último día.
        for (int indice = 0; indice < cantidad; indice++) {
            LocalDate fecha = inicio.plusDays(indice);
            DayOfWeek dia = fecha.getDayOfWeek();
            int categoria = festivos.contains(fecha) ? 3
                    : (dia == DayOfWeek.SATURDAY || dia == DayOfWeek.SUNDAY ? 2 : 1);
            preparados.add(new DiaPreparado(fecha, categoria, DIAS.get(dia.getValue() - 1)));
        }
        return unidadDeTrabajo.ejecutar(() -> {
            calendarioRepositorio.bloquearAnio(anio);
            // Se consultan dentro de la misma transacción que el reemplazo.
            var laboral = tipoRepositorio.obtener(1);
            var finDeSemana = tipoRepositorio.obtener(2);
            var festivo = tipoRepositorio.obtener(3);
            if (laboral.isEmpty() || finDeSemana.isEmpty() || festivo.isEmpty()) {
                throw new ErrorNegocio(CodigoError.TIPOS_REQUERIDOS_AUSENTES,
                        "Se requieren los tipos 1, 2 y 3 para generar el calendario.");
            }
            List<Tipo> categorias = List.of(laboral.get(), finDeSemana.get(), festivo.get());
            List<Calendario> dias = preparados.stream().map(dia -> new Calendario(
                    null, dia.fecha(), categorias.get(dia.categoria() - 1), dia.descripcion())).toList();
            calendarioRepositorio.reemplazarAnio(anio, dias);
            return true;
        });
    }

    public List<Calendario> listar(int anio) {
        validarAnio(anio);
        return calendarioRepositorio.listarAnio(anio).stream()
                .sorted(Comparator.comparing(Calendario::fecha)).toList();
    }

    private static void validarAnio(int anio) {
        if (anio < 1 || anio > 9999) {
            throw new ErrorNegocio(CodigoError.SOLICITUD_INVALIDA,
                    "El año debe estar entre 1 y 9999.");
        }
    }

    private record DiaPreparado(LocalDate fecha, int categoria, String descripcion) { }
}
