package calendario;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "festivos.real.url", matches = "https?://.+")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = CalendarioApplication.class)
class FestivosRealTest {
    private static final EmbeddedPostgres POSTGRES = postgres();
    @LocalServerPort private int puerto;

    private static EmbeddedPostgres postgres() {
        try {
            return EmbeddedPostgres.builder().setPort(0).start();
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void configurar(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        r.add("spring.datasource.username", () -> "postgres");
        r.add("spring.datasource.password", () -> "postgres");
        r.add("festivos.url", () -> System.getProperty("festivos.real.url"));
    }

    @AfterAll
    static void cerrar() throws IOException {
        POSTGRES.close();
    }

    @Test
    void calendarioPersistidoCoincideConApiFestivosReal() throws Exception {
        ObjectMapper json = new ObjectMapper();
        try (HttpClient http = HttpClient.newHttpClient()) {
            String festivosUrl = System.getProperty("festivos.real.url").replaceAll("/$", "");
            var origen = http.send(HttpRequest.newBuilder(URI.create(festivosUrl + "/api/festivos/obtener/2023"))
                    .timeout(java.time.Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, origen.statusCode(), origen.body());
            var fechasEsperadas = new HashSet<String>();
            for (JsonNode festivo : json.readTree(origen.body())) {
                fechasEsperadas.add(festivo.get("fecha").asText());
            }
            assertFalse(fechasEsperadas.isEmpty(), "La API real necesita su catálogo de festivos cargado");
            String base = "http://127.0.0.1:" + puerto;
            var generado = http.send(HttpRequest.newBuilder(URI.create(base + "/api/calendario/generar/2023"))
                    .timeout(java.time.Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, generado.statusCode(), generado.body());
            assertEquals("true", generado.body());
            var listado = http.send(HttpRequest.newBuilder(URI.create(base + "/api/calendario/listar/2023"))
                    .timeout(java.time.Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, listado.statusCode(), listado.body());
            JsonNode dias = json.readTree(listado.body());
            assertEquals(365, dias.size());
            var fechasObtenidas = new HashSet<String>();
            for (JsonNode dia : dias) {
                if (dia.get("tipo").get("id").asInt() == 3) {
                    fechasObtenidas.add(dia.get("fecha").asText());
                }
            }
            assertEquals(fechasEsperadas, fechasObtenidas);
        }
    }
}
