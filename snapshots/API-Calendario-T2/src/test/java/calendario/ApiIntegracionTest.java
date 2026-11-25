package calendario;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = CalendarioApplication.class)
class ApiIntegracionTest {
    private static final EmbeddedPostgres POSTGRES = postgres();
    private static final AtomicReference<String> RESPUESTA = new AtomicReference<>();
    private static final HttpServer FESTIVOS = festivos();
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    @LocalServerPort private int puerto;
    @Autowired private JdbcTemplate jdbc;

    private static EmbeddedPostgres postgres() {
        try {
            return EmbeddedPostgres.builder().setPort(0).start();
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static HttpServer festivos() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/api/festivos/obtener/", intercambio -> {
                String ruta = intercambio.getRequestURI().getPath();
                String anio = "%04d".formatted(Integer.parseInt(ruta.substring(ruta.lastIndexOf('/') + 1)));
                String cuerpo = RESPUESTA.get();
                if (cuerpo == null) {
                    cuerpo = "[{\"festivo\":\"Año nuevo\",\"fecha\":\"" + anio + "-01-01\"}]";
                }
                byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
                intercambio.getResponseHeaders().set("Content-Type", "application/json");
                intercambio.sendResponseHeaders(200, bytes.length);
                intercambio.getResponseBody().write(bytes);
                intercambio.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void configurar(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        r.add("spring.datasource.username", () -> "postgres");
        r.add("spring.datasource.password", () -> "postgres");
        r.add("festivos.url", () -> "http://127.0.0.1:" + FESTIVOS.getAddress().getPort());
    }

    @AfterAll
    static void cerrar() throws IOException {
        FESTIVOS.stop(0);
        HTTP.close();
        POSTGRES.close();
    }

    @BeforeEach
    void limpiar() {
        jdbc.update("DELETE FROM calendario");
        jdbc.update("DELETE FROM tipo WHERE id > 3");
        jdbc.update("INSERT INTO tipo(id,tipo) VALUES (1,'Dia laboral'),(2,'Fin de Semana'),(3,'Dia festivo') "
                + "ON CONFLICT(id) DO UPDATE SET tipo = EXCLUDED.tipo");
        RESPUESTA.set(null);
    }

    private HttpResponse<String> pedir(String metodo, String ruta, String cuerpo) throws Exception {
        HttpRequest.Builder solicitud = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + puerto + ruta))
                .timeout(Duration.ofSeconds(20));
        if (cuerpo == null) {
            solicitud.method(metodo, HttpRequest.BodyPublishers.noBody());
        } else {
            solicitud.header("Content-Type", "application/json")
                    .method(metodo, HttpRequest.BodyPublishers.ofString(cuerpo));
        }
        return HTTP.send(solicitud.build(), HttpResponse.BodyHandlers.ofString());
    }

    private void generar(int anio) throws Exception {
        var r = pedir("POST", "/api/calendario/generar/" + anio, null);
        assertEquals(200, r.statusCode(), r.body());
        assertEquals("true", r.body());
    }

    @Test
    void generaConsultaRegeneraYRespetaOtrosAnios() throws Exception {
        assertEquals("[]", pedir("GET", "/api/calendario/listar/2024", null).body());
        generar(2023);
        generar(2024);
        generar(2024);
        JsonNode dias = json.readTree(pedir("GET", "/api/calendario/listar/2024", null).body());
        assertEquals(366, dias.size());
        assertEquals("2024-01-01", dias.get(0).get("fecha").asText());
        assertEquals(3, dias.get(0).get("tipo").get("id").asInt());
        assertEquals("Lunes", dias.get(0).get("descripcion").asText());
        assertEquals("2024-12-31", dias.get(365).get("fecha").asText());
        JsonNode comun = json.readTree(pedir("GET", "/api/calendario/listar/2023", null).body());
        assertEquals(365, comun.size());
        assertEquals(3, comun.get(0).get("tipo").get("id").asInt());
        assertEquals("Domingo", comun.get(0).get("descripcion").asText());
        assertEquals(731, jdbc.queryForObject("SELECT count(*) FROM calendario", Integer.class));
    }

    @Test
    void persisteLosLimitesDelRangoSinCambiarFechas() throws Exception {
        for (int anio : new int[]{1, 9999}) {
            generar(anio);
            JsonNode dias = json.readTree(pedir("GET", "/api/calendario/listar/" + anio, null).body());
            assertEquals(365, dias.size());
            assertEquals("%04d-01-01".formatted(anio), dias.get(0).get("fecha").asText());
            assertEquals("%04d-12-31".formatted(anio), dias.get(364).get("fecha").asText());
        }
    }

    @Test
    void crudRealProtegeReferenciasYConservaIdentidad() throws Exception {
        var creado = pedir("POST", "/api/tipos", "{\"id\":4,\"tipo\":\"  Extra  \"}");
        assertEquals(201, creado.statusCode(), creado.body());
        assertEquals("/api/tipos/4", creado.headers().firstValue("Location").orElseThrow());
        assertEquals("Extra", json.readTree(creado.body()).get("tipo").asText());
        assertEquals(409, pedir("POST", "/api/tipos", "{\"id\":4,\"tipo\":\"Otro\"}").statusCode());
        assertEquals(200, pedir("PUT", "/api/tipos/4", "{\"tipo\":\"Nuevo\"}").statusCode());
        assertEquals("Nuevo", json.readTree(pedir("GET", "/api/tipos/4", null).body()).get("tipo").asText());
        assertEquals(204, pedir("DELETE", "/api/tipos/4", null).statusCode());
        assertEquals(404, pedir("DELETE", "/api/tipos/4", null).statusCode());
        assertEquals(200, pedir("PUT", "/api/tipos/3", "{\"tipo\":\"Celebracion\"}").statusCode());
        generar(2024);
        assertEquals(409, pedir("DELETE", "/api/tipos/3", null).statusCode());
        JsonNode dias = json.readTree(pedir("GET", "/api/calendario/listar/2024", null).body());
        assertEquals("Celebracion", dias.get(0).get("tipo").get("tipo").asText());
    }

    @Test
    void categoriaAusenteBloqueaHastaRestaurarla() throws Exception {
        assertEquals(204, pedir("DELETE", "/api/tipos/3", null).statusCode());
        assertEquals(409, pedir("POST", "/api/calendario/generar/2024", null).statusCode());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM calendario", Integer.class));
        assertEquals(201, pedir("POST", "/api/tipos", "{\"id\":3,\"tipo\":\"Festivo\"}").statusCode());
        generar(2024);
    }

    @Test
    void falloDeApiFestivosNoDestruyeCalendarioYConsultaNoDependeDeElla() throws Exception {
        generar(2024);
        String antes = pedir("GET", "/api/calendario/listar/2024", null).body();
        RESPUESTA.set("respuesta rota");
        assertEquals(502, pedir("POST", "/api/calendario/generar/2024", null).statusCode());
        assertEquals(antes, pedir("GET", "/api/calendario/listar/2024", null).body());
        assertEquals(200, pedir("GET", "/api/tipos", null).statusCode());
    }

    @Test
    void generacionesConcurrentesNoDuplicanNiDejanAniosParciales() throws Exception {
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> { generar(2024); return null; });
            var b = pool.submit(() -> { generar(2024); return null; });
            a.get();
            b.get();
        }
        assertEquals(366, jdbc.queryForObject("SELECT count(*) FROM calendario", Integer.class));
        assertEquals(366, jdbc.queryForObject("SELECT count(DISTINCT fecha) FROM calendario", Integer.class));
    }

    @Test
    void solicitudesInvalidasResponden400SinEscrituras() throws Exception {
        for (String cuerpo : new String[]{"{}", "{\"id\":0,\"tipo\":\"Nombre\"}",
                "{\"id\":4,\"tipo\":\" \"}", "{\"id\":4,\"tipo\":\"Extra\",\"otro\":1}"}) {
            assertEquals(400, pedir("POST", "/api/tipos", cuerpo).statusCode());
        }
        assertEquals(400, pedir("POST", "/api/calendario/generar/10000", null).statusCode());
        assertEquals(400, pedir("GET", "/api/calendario/listar/abc", null).statusCode());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM calendario", Integer.class));
    }
}
