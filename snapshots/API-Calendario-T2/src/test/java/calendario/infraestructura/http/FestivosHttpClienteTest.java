package calendario.infraestructura.http;

import calendario.core.modelo.CodigoError;
import calendario.core.modelo.ErrorNegocio;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class FestivosHttpClienteTest {
    private HttpServer servidor;
    private ExecutorService ejecutor;
    private int estado;
    private String respuesta;
    private Duration demora;

    @BeforeEach
    void iniciar() throws IOException {
        estado = 200;
        respuesta = "[]";
        demora = Duration.ZERO;
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ejecutor = Executors.newVirtualThreadPerTaskExecutor();
        servidor.setExecutor(ejecutor);
        servidor.createContext("/api/festivos/obtener/2024", intercambio -> {
            try {
                Thread.sleep(demora);
                byte[] cuerpo = respuesta.getBytes(StandardCharsets.UTF_8);
                intercambio.getResponseHeaders().set("Content-Type", "application/json");
                intercambio.sendResponseHeaders(estado, cuerpo.length);
                intercambio.getResponseBody().write(cuerpo);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                intercambio.close();
            }
        });
        servidor.start();
    }

    @AfterEach
    void cerrar() {
        servidor.stop(0);
        ejecutor.shutdownNow();
        ejecutor.close();
    }

    private FestivosHttpCliente cliente(Duration timeout) {
        URI base = URI.create("http://127.0.0.1:" + servidor.getAddress().getPort());
        return new FestivosHttpCliente(base, timeout, new ObjectMapper());
    }

    private void comprobarError(CodigoError codigo) {
        ErrorNegocio error = assertThrows(ErrorNegocio.class,
                () -> cliente(Duration.ofSeconds(2)).obtener(2024));
        assertEquals(codigo, error.codigo());
    }

    @Test
    void transformaFechasYEliminaDuplicados() {
        respuesta = """
                [{"festivo":"Año nuevo","fecha":"2024-01-01"},
                 {"festivo":"Otro nombre","fecha":"2024-01-01"},
                 {"festivo":"Festivo","fecha":"2024-02-29"}]
                """;
        assertEquals(Set.of(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 2, 29)),
                cliente(Duration.ofSeconds(2)).obtener(2024));
    }

    @Test
    void aceptaListaVacia() {
        assertTrue(cliente(Duration.ofSeconds(2)).obtener(2024).isEmpty());
    }

    @Test
    void rechazaErroresHttp() {
        estado = 500;
        comprobarError(CodigoError.RESPUESTA_FESTIVOS_INVALIDA);
    }

    @Test
    void rechazaJsonInvalido() {
        respuesta = "no es JSON";
        comprobarError(CodigoError.RESPUESTA_FESTIVOS_INVALIDA);
    }

    @Test
    void rechazaObjetoEnLugarDeLista() {
        respuesta = "{}";
        comprobarError(CodigoError.RESPUESTA_FESTIVOS_INVALIDA);
    }

    @Test
    void rechazaCamposAusentesOConTiposIncorrectos() {
        for (String cuerpo : new String[]{
                "[null]", "[{\"fecha\":\"2024-01-01\"}]",
                "[{\"festivo\":1,\"fecha\":\"2024-01-01\"}]",
                "[{\"festivo\":\" \u0020\",\"fecha\":\"2024-01-01\"}]",
                "[{\"festivo\":\"Festivo\",\"fecha\":20240101}]"}) {
            respuesta = cuerpo;
            comprobarError(CodigoError.RESPUESTA_FESTIVOS_INVALIDA);
        }
    }

    @Test
    void rechazaFechasImposiblesFueraDelAnioONoIso() {
        for (String fecha : new String[]{"2024-02-30", "2023-01-01", "2024-1-1"}) {
            respuesta = "[{\"festivo\":\"Festivo\",\"fecha\":\"" + fecha + "\"}]";
            comprobarError(CodigoError.RESPUESTA_FESTIVOS_INVALIDA);
        }
    }

    @Test
    void diferenciaTimeoutDeRespuestaInvalida() {
        demora = Duration.ofMillis(300);
        ErrorNegocio error = assertThrows(ErrorNegocio.class,
                () -> cliente(Duration.ofMillis(50)).obtener(2024));
        assertEquals(CodigoError.TIEMPO_FESTIVOS_AGOTADO, error.codigo());
    }

    @Test
    void diferenciaConexionRechazada() {
        FestivosHttpCliente cliente = cliente(Duration.ofSeconds(2));
        servidor.stop(0);
        ErrorNegocio error = assertThrows(ErrorNegocio.class, () -> cliente.obtener(2024));
        assertEquals(CodigoError.FESTIVOS_NO_DISPONIBLE, error.codigo());
    }
}
