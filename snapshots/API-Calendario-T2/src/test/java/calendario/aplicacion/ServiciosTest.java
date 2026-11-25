package calendario.aplicacion;

import calendario.core.modelo.*;
import calendario.core.puertos.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class ServiciosTest {
    private final List<String> eventos = new ArrayList<>();
    private final Tipos tipos = new Tipos();
    private final Dias dias = new Dias();
    private final Trabajo trabajo = new Trabajo();
    private Set<LocalDate> festivos = Set.of();
    private RuntimeException falloProveedor;
    private int llamadasProveedor;
    private CalendarioServicio calendario;
    private TipoServicio servicioTipos;

    @BeforeEach
    void preparar() {
        tipos.datos.put(1, new Tipo(1, "Laboral renombrado"));
        tipos.datos.put(2, new Tipo(2, "Descanso"));
        tipos.datos.put(3, new Tipo(3, "Celebración"));
        calendario = new CalendarioServicio(dias, tipos, anio -> {
            assertFalse(trabajo.activa, "La llamada externa no pertenece a la transacción");
            llamadasProveedor++;
            eventos.add("proveedor");
            if (falloProveedor != null) throw falloProveedor;
            return festivos;
        }, trabajo);
        servicioTipos = new TipoServicio(tipos, trabajo);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2023, 2024, 9999})
    void generaCoberturaCompletaSinDesbordar(int anio) {
        assertTrue(calendario.generar(anio));
        List<Calendario> resultado = dias.datos.get(anio);
        assertEquals(LocalDate.of(anio, 1, 1).lengthOfYear(), resultado.size());
        assertEquals(LocalDate.of(anio, 1, 1), resultado.getFirst().fecha());
        assertEquals(LocalDate.of(anio, 12, 31), resultado.getLast().fecha());
        assertEquals(resultado.size(), resultado.stream().map(Calendario::fecha).distinct().count());
        assertTrue(resultado.stream().allMatch(d -> d.id() == null));
        assertEquals(List.of("proveedor", "inicio", "bloquear:" + anio,
                "obtener:1", "obtener:2", "obtener:3", "reemplazar:" + anio, "commit"), eventos);
    }

    @Test
    void clasificaPorIdentidadConPrioridadFestivoYDescripcionEspanola() {
        festivos = Set.of(LocalDate.of(2023, 1, 1));
        calendario.generar(2023);
        List<Calendario> resultado = dias.datos.get(2023);
        assertEquals(3, resultado.getFirst().tipo().id());
        assertEquals("Celebración", resultado.getFirst().tipo().tipo());
        assertEquals("Domingo", resultado.getFirst().descripcion());
        assertEquals(1, resultado.get(1).tipo().id());
        assertEquals("Lunes", resultado.get(1).descripcion());
        assertEquals(2, resultado.get(6).tipo().id());
        assertEquals("Sábado", resultado.get(6).descripcion());
        assertEquals("Miércoles", resultado.get(3).descripcion());
    }

    @Test
    void regeneraSoloAnioSolicitado() {
        calendario.generar(2023);
        List<Calendario> anterior = dias.datos.get(2023);
        calendario.generar(2024);
        festivos = Set.of(LocalDate.of(2024, 1, 2));
        calendario.generar(2024);
        assertSame(anterior, dias.datos.get(2023));
        assertEquals(366, dias.datos.get(2024).size());
        assertEquals(3, dias.datos.get(2024).get(1).tipo().id());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 10000, Integer.MAX_VALUE})
    void rechazaAnioAntesDeConsultarPuertos(int anio) {
        error(CodigoError.SOLICITUD_INVALIDA, () -> calendario.generar(anio));
        error(CodigoError.SOLICITUD_INVALIDA, () -> calendario.listar(anio));
        assertTrue(eventos.isEmpty());
        assertEquals(0, llamadasProveedor);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void requiereCadaCategoriaAntesDeReemplazar(int id) {
        tipos.datos.remove(id);
        error(CodigoError.TIPOS_REQUERIDOS_AUSENTES, () -> calendario.generar(2024));
        assertTrue(dias.datos.isEmpty());
        assertFalse(eventos.contains("reemplazar:2024"));
    }

    @Test
    void rechazaRespuestaNulaFechasNulasYFueraDelAnioSinTransaccion() {
        festivos = null;
        error(CodigoError.RESPUESTA_FESTIVOS_INVALIDA, () -> calendario.generar(2024));
        festivos = new HashSet<>();
        festivos.add(null);
        error(CodigoError.RESPUESTA_FESTIVOS_INVALIDA, () -> calendario.generar(2024));
        festivos = Set.of(LocalDate.of(2023, 12, 31));
        error(CodigoError.RESPUESTA_FESTIVOS_INVALIDA, () -> calendario.generar(2024));
        assertEquals(0, trabajo.llamadas);
        assertTrue(dias.datos.isEmpty());
    }

    @Test
    void propagaFalloProveedorSinAbrirUnidadDeTrabajo() {
        falloProveedor = new ErrorNegocio(CodigoError.TIEMPO_FESTIVOS_AGOTADO, "Tiempo agotado");
        assertSame(falloProveedor, assertThrows(ErrorNegocio.class, () -> calendario.generar(2024)));
        assertEquals(0, trabajo.llamadas);
    }

    @Test
    void noRetornaExitoSiFallaConfirmacion() {
        trabajo.falloCommit = new IllegalStateException("commit fallido");
        assertSame(trabajo.falloCommit,
                assertThrows(IllegalStateException.class, () -> calendario.generar(2024)));
        // Este doble verifica propagación, no demuestra rollback real de persistencia.
    }

    @Test
    void listaOrdenadoInmutableSinProveedorNiTransaccion() {
        Tipo tipo = tipos.datos.get(1);
        Calendario primero = new Calendario(1, LocalDate.of(2024, 1, 1), tipo, "Lunes");
        Calendario segundo = new Calendario(2, LocalDate.of(2024, 1, 2), tipo, "Martes");
        dias.datos.put(2024, new ArrayList<>(List.of(segundo, primero)));
        List<Calendario> resultado = calendario.listar(2024);
        assertEquals(List.of(primero, segundo), resultado);
        assertThrows(UnsupportedOperationException.class, resultado::clear);
        assertTrue(calendario.listar(2023).isEmpty());
        assertEquals(0, llamadasProveedor);
        assertEquals(0, trabajo.llamadas);
    }

    @Test
    void crudPermiteExtrasYRenombrarSinCambiarIdentidad() {
        Tipo creado = servicioTipos.crear(4, "  Extra  ");
        assertEquals(new Tipo(4, "Extra"), creado);
        assertEquals(creado, servicioTipos.obtener(4));
        assertEquals(new Tipo(4, "Otro"), servicioTipos.actualizar(4, " Otro "));
        assertEquals(new Tipo(1, "Nuevo laboral"), servicioTipos.actualizar(1, "Nuevo laboral"));
        assertEquals(List.of(1, 2, 3, 4), servicioTipos.listar().stream().map(Tipo::id).toList());
        assertThrows(UnsupportedOperationException.class, () -> servicioTipos.listar().clear());
        servicioTipos.eliminar(4);
        error(CodigoError.TIPO_NO_ENCONTRADO, () -> servicioTipos.obtener(4));
        assertEquals(4, trabajo.llamadas);
        assertEquals(0, llamadasProveedor);
    }

    @Test
    void erroresCrudNoSobrescribenNiEliminanReferencias() {
        error(CodigoError.TIPO_EXISTENTE, () -> servicioTipos.crear(1, "Duplicado"));
        assertEquals("Laboral renombrado", tipos.datos.get(1).tipo());
        error(CodigoError.TIPO_NO_ENCONTRADO, () -> servicioTipos.actualizar(90, "Nada"));
        error(CodigoError.TIPO_NO_ENCONTRADO, () -> servicioTipos.eliminar(90));
        error(CodigoError.TIPO_NO_ENCONTRADO, () -> servicioTipos.obtener(90));
        tipos.enUso.add(1);
        error(CodigoError.TIPO_EN_USO, () -> servicioTipos.eliminar(1));
        assertTrue(tipos.datos.containsKey(1));
    }

    @Test
    void validaEntradasCrudSinTocarPersistencia() {
        for (Integer id : Arrays.asList(null, 0, -1)) {
            error(CodigoError.SOLICITUD_INVALIDA, () -> servicioTipos.crear(id, "Nombre"));
            error(CodigoError.SOLICITUD_INVALIDA, () -> servicioTipos.obtener(id));
            error(CodigoError.SOLICITUD_INVALIDA, () -> servicioTipos.actualizar(id, "Nombre"));
            error(CodigoError.SOLICITUD_INVALIDA, () -> servicioTipos.eliminar(id));
        }
        for (String nombre : Arrays.asList(null, "", "   ", "x".repeat(101))) {
            error(CodigoError.SOLICITUD_INVALIDA, () -> servicioTipos.crear(4, nombre));
            error(CodigoError.SOLICITUD_INVALIDA, () -> servicioTipos.actualizar(1, nombre));
        }
        assertEquals(new Tipo(4, "x".repeat(100)), servicioTipos.crear(4, "x".repeat(100)));
    }

    private void error(CodigoError codigo, Runnable accion) {
        assertEquals(codigo, assertThrows(ErrorNegocio.class, accion::run).codigo());
    }

    private class Trabajo implements UnidadDeTrabajo {
        boolean activa;
        int llamadas;
        RuntimeException falloCommit;

        @Override
        public <T> T ejecutar(Supplier<T> accion) {
            llamadas++;
            activa = true;
            eventos.add("inicio");
            try {
                T resultado = accion.get();
                if (falloCommit != null) throw falloCommit;
                eventos.add("commit");
                return resultado;
            } finally {
                activa = false;
            }
        }
    }

    private class Tipos implements TipoRepositorio {
        final Map<Integer, Tipo> datos = new TreeMap<>();
        final Set<Integer> enUso = new HashSet<>();

        @Override public Tipo crear(Tipo tipo) {
            assertTrue(trabajo.activa);
            assertFalse(datos.containsKey(tipo.id()), "Crear no es upsert");
            datos.put(tipo.id(), tipo);
            return tipo;
        }
        @Override public Optional<Tipo> obtener(Integer id) {
            eventos.add("obtener:" + id);
            return Optional.ofNullable(datos.get(id));
        }
        @Override public List<Tipo> listar() { return new ArrayList<>(datos.values()); }
        @Override public Tipo actualizar(Tipo tipo) {
            assertTrue(trabajo.activa);
            assertTrue(datos.containsKey(tipo.id()));
            datos.put(tipo.id(), tipo);
            return tipo;
        }
        @Override public void eliminar(Integer id) {
            assertTrue(trabajo.activa);
            datos.remove(id);
        }
        @Override public boolean estaEnUso(Integer id) {
            assertTrue(trabajo.activa);
            return enUso.contains(id);
        }
    }

    private class Dias implements CalendarioRepositorio {
        final Map<Integer, List<Calendario>> datos = new HashMap<>();
        @Override public List<Calendario> listarAnio(int anio) {
            return datos.getOrDefault(anio, List.of());
        }
        @Override public void reemplazarAnio(int anio, List<Calendario> nuevos) {
            assertTrue(trabajo.activa);
            assertTrue(eventos.contains("bloquear:" + anio));
            eventos.add("reemplazar:" + anio);
            datos.put(anio, List.copyOf(nuevos));
        }
        @Override public void bloquearAnio(int anio) {
            assertTrue(trabajo.activa);
            eventos.add("bloquear:" + anio);
        }
    }
}
