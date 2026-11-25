package calendario.infraestructura.persistencia;

import calendario.core.modelo.Calendario;
import calendario.core.modelo.CodigoError;
import calendario.core.modelo.ErrorNegocio;
import calendario.core.modelo.Tipo;
import calendario.core.puertos.CalendarioRepositorio;
import calendario.core.puertos.TipoRepositorio;
import calendario.core.puertos.UnidadDeTrabajo;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        classes = PersistenciaPostgresTest.TestConfig.class)
class PersistenciaPostgresTest {
    private static final EmbeddedPostgres POSTGRES = iniciar();

    @TestConfiguration
    @EnableAutoConfiguration
    @Import(PersistenciaConfiguracion.class)
    static class TestConfig { }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.open-in-view", () -> "false");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    private static EmbeddedPostgres iniciar() {
        try {
            return EmbeddedPostgres.builder().setPort(0).start();
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @AfterAll
    static void cerrarPostgres() throws IOException { POSTGRES.close(); }

    @Autowired TipoRepositorio tipos;
    @Autowired CalendarioRepositorio calendarios;
    @Autowired UnidadDeTrabajo tx;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void limpiar() {
        jdbc.update("DELETE FROM calendario");
        jdbc.update("DELETE FROM tipo WHERE id > 3");
    }

    @Test
    void migracionInicialEsUnicaYNoResiembraAlMigrarOtraVez() {
        assertEquals(List.of(new Tipo(1, "Dia laboral"), new Tipo(2, "Fin de Semana"),
                new Tipo(3, "Dia festivo")), tipos.listar());
        tx.ejecutar(() -> { tipos.eliminar(3); return null; });
        try {
            org.flywaydb.core.Flyway.configure()
                    .dataSource(POSTGRES.getJdbcUrl("postgres", "postgres"), "postgres", "postgres")
                    .load().migrate();
            assertTrue(tipos.obtener(3).isEmpty());
        } finally {
            tx.ejecutar(() -> tipos.crear(new Tipo(3, "Dia festivo")));
        }
    }

    @Test
    void crudNoHaceMergeYActualizaSoloExistentes() {
        tx.ejecutar(() -> tipos.crear(new Tipo(8, "Especial")));
        assertEquals(new Tipo(8, "Especial"), tipos.obtener(8).orElseThrow());
        error(CodigoError.TIPO_EXISTENTE,
                () -> tx.ejecutar(() -> tipos.crear(new Tipo(8, "No sobrescribir"))));
        assertEquals("Especial", tipos.obtener(8).orElseThrow().tipo());
        assertEquals(new Tipo(8, "Otro"),
                tx.ejecutar(() -> tipos.actualizar(new Tipo(8, "Otro"))));
        tx.ejecutar(() -> { tipos.eliminar(8); return null; });
        error(CodigoError.TIPO_NO_ENCONTRADO,
                () -> tx.ejecutar(() -> tipos.actualizar(new Tipo(8, "No insertar"))));
        error(CodigoError.TIPO_NO_ENCONTRADO,
                () -> tx.ejecutar(() -> { tipos.eliminar(8); return null; }));
        assertTrue(tipos.obtener(8).isEmpty());
    }

    @Test
    void regeneracionOrdenadaAisladaConTipoMaterializadoYLimite9999() {
        guardar(2024, List.of(dia(2024, 2, 2, 1), dia(2024, 1, 1, 2)));
        guardar(9999, List.of(dia(9999, 12, 31, 3)));
        assertEquals(List.of(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 2, 2)),
                calendarios.listarAnio(2024).stream().map(Calendario::fecha).toList());
        assertEquals("Fin de Semana", calendarios.listarAnio(2024).getFirst().tipo().tipo());
        guardar(2024, List.of(dia(2024, 3, 1, 1)));
        assertEquals(1, calendarios.listarAnio(2024).size());
        assertEquals(LocalDate.of(9999, 12, 31), calendarios.listarAnio(9999).getFirst().fecha());
        assertTrue(calendarios.listarAnio(2025).isEmpty());
    }

    @Test
    void rollbackConservaAnioAnteriorAnteUnicidadYFk() {
        var anterior = List.of(dia(2024, 1, 1, 1));
        guardar(2024, anterior);
        var persistidos = calendarios.listarAnio(2024);
        assertThrows(RuntimeException.class, () -> guardar(2024,
                List.of(dia(2024, 2, 1, 1), dia(2024, 2, 1, 2))));
        assertEquals(persistidos, calendarios.listarAnio(2024));
        assertThrows(RuntimeException.class, () -> guardar(2024, List.of(dia(2024, 3, 1, 98))));
        assertEquals(persistidos, calendarios.listarAnio(2024));
        assertThrows(IllegalStateException.class, () -> tx.ejecutar(() -> {
            calendarios.reemplazarAnio(2024, List.of(dia(2024, 4, 1, 1)));
            throw new IllegalStateException("fallo posterior a escritura");
        }));
        assertEquals(persistidos, calendarios.listarAnio(2024));
    }

    @Test
    void fkImpideEliminarTipoEnUsoSinEliminarDias() {
        guardar(2024, List.of(dia(2024, 1, 1, 1)));
        assertTrue(tipos.estaEnUso(1));
        assertFalse(tipos.estaEnUso(2));
        error(CodigoError.TIPO_EN_USO,
                () -> tx.ejecutar(() -> { tipos.eliminar(1); return null; }));
        assertEquals(1, calendarios.listarAnio(2024).size());
    }

    @Test
    void generacionesConcurrentesSeSerializanHastaCommit() throws Exception {
        var adquirido = new CountDownLatch(1);
        var liberar = new CountDownLatch(1);
        var segundoInicio = new CountDownLatch(1);
        var segundoAdquirido = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var primero = pool.submit(() -> tx.ejecutar(() -> {
                calendarios.bloquearAnio(2024);
                adquirido.countDown();
                esperar(liberar);
                calendarios.reemplazarAnio(2024, List.of(dia(2024, 1, 1, 1)));
                return null;
            }));
            try {
                assertTrue(adquirido.await(10, TimeUnit.SECONDS));
                var segundo = pool.submit(() -> tx.ejecutar(() -> {
                    segundoInicio.countDown();
                    calendarios.bloquearAnio(2024);
                    segundoAdquirido.countDown();
                    calendarios.reemplazarAnio(2024, List.of(dia(2024, 2, 1, 2)));
                    return null;
                }));
                assertTrue(segundoInicio.await(10, TimeUnit.SECONDS));
                assertFalse(segundoAdquirido.await(300, TimeUnit.MILLISECONDS));
                liberar.countDown();
                primero.get(10, TimeUnit.SECONDS);
                segundo.get(10, TimeUnit.SECONDS);
            } finally {
                liberar.countDown();
            }
        }
        assertEquals(List.of(LocalDate.of(2024, 2, 1)),
                calendarios.listarAnio(2024).stream().map(Calendario::fecha).toList());
    }

    @Test
    void eliminacionConcurrenteEsperaElCommitYNoDejaDiasHuerfanos() throws Exception {
        var insertado = new CountDownLatch(1);
        var liberar = new CountDownLatch(1);
        var eliminacionIniciada = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var generacion = pool.submit(() -> tx.ejecutar(() -> {
                calendarios.bloquearAnio(2024);
                calendarios.reemplazarAnio(2024, List.of(dia(2024, 1, 1, 1)));
                insertado.countDown();
                esperar(liberar);
                return null;
            }));
            try {
                assertTrue(insertado.await(10, TimeUnit.SECONDS));
                var eliminacion = pool.submit(() -> {
                    eliminacionIniciada.countDown();
                    return assertThrows(ErrorNegocio.class, () -> tx.ejecutar(() -> {
                        tipos.eliminar(1);
                        return null;
                    }));
                });
                assertTrue(eliminacionIniciada.await(10, TimeUnit.SECONDS));
                liberar.countDown();
                generacion.get(10, TimeUnit.SECONDS);
                assertEquals(CodigoError.TIPO_EN_USO, eliminacion.get(10, TimeUnit.SECONDS).codigo());
            } finally {
                liberar.countDown();
            }
        }
        assertTrue(tipos.obtener(1).isPresent());
        assertEquals(1, calendarios.listarAnio(2024).size());
    }

    private void guardar(int anio, List<Calendario> dias) {
        tx.ejecutar(() -> {
            calendarios.bloquearAnio(anio);
            calendarios.reemplazarAnio(anio, dias);
            return null;
        });
    }

    private static Calendario dia(int anio, int mes, int dia, int tipo) {
        return new Calendario(null, LocalDate.of(anio, mes, dia), new Tipo(tipo, "Tipo"), "Lunes");
    }

    private static void error(CodigoError codigo, Runnable accion) {
        assertEquals(codigo, assertThrows(ErrorNegocio.class, accion::run).codigo());
    }

    private static void esperar(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Barrera agotada");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
