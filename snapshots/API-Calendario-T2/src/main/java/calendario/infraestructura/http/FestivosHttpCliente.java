package calendario.infraestructura.http;

import calendario.core.modelo.CodigoError;
import calendario.core.modelo.ErrorNegocio;
import calendario.core.puertos.FestivosProveedor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Adaptador HTTP de la lista anual de festivos; no calcula fechas festivas. */
public final class FestivosHttpCliente implements FestivosProveedor, AutoCloseable {
    private final String base;
    private final Duration timeout;
    private final ObjectMapper json;
    private final HttpClient http;

    public FestivosHttpCliente(URI base, Duration timeout, ObjectMapper json) {
        Objects.requireNonNull(base, "La URL de Festivos es obligatoria");
        if (!("http".equals(base.getScheme()) || "https".equals(base.getScheme()))
                || base.getHost() == null || base.getUserInfo() != null
                || base.getQuery() != null || base.getFragment() != null) {
            throw new IllegalArgumentException("La URL base de Festivos debe ser HTTP(S), sin credenciales ni consulta");
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("El tiempo de espera debe ser positivo");
        }
        this.base = base.toString().replaceAll("/+$", "");
        this.timeout = timeout;
        this.json = Objects.requireNonNull(json);
        this.http = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    @Override
    public Set<LocalDate> obtener(int anio) {
        HttpRequest solicitud = HttpRequest.newBuilder(URI.create(base + "/api/festivos/obtener/" + anio))
                .header("Accept", "application/json").timeout(timeout).GET().build();
        HttpResponse<String> respuesta;
        try {
            respuesta = http.send(solicitud, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpTimeoutException e) {
            throw new ErrorNegocio(CodigoError.TIEMPO_FESTIVOS_AGOTADO, "API Festivos excedio el tiempo de espera.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ErrorNegocio(CodigoError.FESTIVOS_NO_DISPONIBLE, "La consulta a API Festivos fue interrumpida.");
        } catch (IOException e) {
            throw new ErrorNegocio(CodigoError.FESTIVOS_NO_DISPONIBLE, "No fue posible comunicarse con API Festivos.");
        }
        if (respuesta.statusCode() != 200) {
            throw respuestaInvalida();
        }
        try {
            JsonNode lista = json.readTree(respuesta.body());
            if (lista == null || !lista.isArray()) throw respuestaInvalida();
            Set<LocalDate> fechas = new HashSet<>();
            for (JsonNode elemento : lista) {
                JsonNode fecha = elemento.get("fecha");
                JsonNode nombre = elemento.get("festivo");
                if (fecha == null || !fecha.isTextual() || !fecha.textValue().matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")
                        || nombre == null || !nombre.isTextual() || nombre.textValue().isBlank()) {
                    throw respuestaInvalida();
                }
                LocalDate dia = LocalDate.parse(fecha.textValue());
                if (dia.getYear() != anio) throw respuestaInvalida();
                fechas.add(dia);
            }
            return Set.copyOf(fechas);
        } catch (IOException | DateTimeParseException e) {
            throw respuestaInvalida();
        }
    }

    private ErrorNegocio respuestaInvalida() {
        return new ErrorNegocio(CodigoError.RESPUESTA_FESTIVOS_INVALIDA,
                "API Festivos devolvio una respuesta invalida.");
    }

    @Override
    public void close() {
        http.close();
    }
}
