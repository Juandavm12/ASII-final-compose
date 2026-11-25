package calendario.infraestructura.web;

import calendario.aplicacion.CalendarioServicio;
import calendario.aplicacion.TipoServicio;
import calendario.core.modelo.CodigoError;
import calendario.core.modelo.ErrorNegocio;
import calendario.core.modelo.Tipo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {CalendarioController.class, TipoController.class}, properties = {
        "spring.jackson.deserialization.fail-on-unknown-properties=true",
        "spring.jackson.deserialization.accept-float-as-int=false",
        "spring.jackson.mapper.allow-coercion-of-scalars=false"})
@Import({ApiExcepcionesHandler.class, JsonConfiguracion.class})
class ControladoresTest {
    @Autowired private MockMvc http;
    @MockitoBean private CalendarioServicio calendario;
    @MockitoBean private TipoServicio tipos;

    @Test
    void generaSinAutenticacionYRetornaBooleano() throws Exception {
        when(calendario.generar(2024)).thenReturn(true);
        http.perform(post("/api/calendario/generar/2024"))
                .andExpect(status().isOk()).andExpect(content().string("true"));
        verify(calendario).generar(2024);
    }

    @Test
    void consultaVaciaNoGenera() throws Exception {
        when(calendario.listar(2024)).thenReturn(List.of());
        http.perform(get("/api/calendario/listar/2024"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        verify(calendario, never()).generar(anyInt());
    }

    @Test
    void crudExponeEstadoLocationYRespuestas() throws Exception {
        Tipo tipo = new Tipo(4, "Extra");
        when(tipos.crear(4, "Extra")).thenReturn(tipo);
        when(tipos.obtener(4)).thenReturn(tipo);
        when(tipos.listar()).thenReturn(List.of(tipo));
        when(tipos.actualizar(4, "Nuevo")).thenReturn(new Tipo(4, "Nuevo"));
        http.perform(post("/api/tipos").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":4,\"tipo\":\"Extra\"}"))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/tipos/4"))
                .andExpect(jsonPath("$.id").value(4)).andExpect(jsonPath("$.tipo").value("Extra"));
        http.perform(get("/api/tipos/4")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(4));
        http.perform(get("/api/tipos")).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(4));
        http.perform(put("/api/tipos/4").contentType(MediaType.APPLICATION_JSON).content("{\"tipo\":\"Nuevo\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tipo").value("Nuevo"));
        http.perform(delete("/api/tipos/4")).andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(tipos).eliminar(4);
    }

    @Test
    void rechazaJsonInvalidoCamposExtrasYCoerciones() throws Exception {
        for (String cuerpo : List.of("{", "null", "[]", "{\"id\":4,\"tipo\":\"Extra\",\"otro\":1}",
                "{\"id\":4.5,\"tipo\":\"Extra\"}", "{\"id\":\"4\",\"tipo\":\"Extra\"}",
                "{\"id\":4,\"tipo\":55}", "{\"id\":2147483648,\"tipo\":\"Extra\"}")) {
            http.perform(post("/api/tipos").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));
        }
        verify(tipos, never()).crear(anyInt(), anyString());
    }

    @Test
    void putNoPermiteCambiarId() throws Exception {
        http.perform(put("/api/tipos/4").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":5,\"tipo\":\"Extra\"}"))
                .andExpect(status().isBadRequest());
        verify(tipos, never()).actualizar(anyInt(), anyString());
    }

    @Test
    void rechazaParametrosNoEnterosYCuerpoDeGeneracion() throws Exception {
        http.perform(get("/api/calendario/listar/abc")).andExpect(status().isBadRequest());
        http.perform(get("/api/tipos/2147483648")).andExpect(status().isBadRequest());
        http.perform(post("/api/calendario/generar/2024").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void traduceTodosLosErroresDeNegocio() throws Exception {
        CodigoError[] codigos = CodigoError.values();
        for (CodigoError codigo : codigos) {
            int estado = switch (codigo) {
                case SOLICITUD_INVALIDA -> 400;
                case TIPO_NO_ENCONTRADO -> 404;
                case TIPO_EXISTENTE, TIPO_EN_USO, TIPOS_REQUERIDOS_AUSENTES -> 409;
                case RESPUESTA_FESTIVOS_INVALIDA -> 502;
                case FESTIVOS_NO_DISPONIBLE -> 503;
                case TIEMPO_FESTIVOS_AGOTADO -> 504;
            };
            doThrow(new ErrorNegocio(codigo, "Mensaje publico")).when(calendario).generar(2024);
            http.perform(post("/api/calendario/generar/2024"))
                    .andExpect(status().is(estado)).andExpect(jsonPath("$.codigo").value(codigo.name()))
                    .andExpect(jsonPath("$.mensaje").value("Mensaje publico"));
        }
    }

    @Test
    void errorInternoNoExponeDetalles() throws Exception {
        when(tipos.listar()).thenThrow(new IllegalStateException("jdbc:postgresql://secreto credencial"));
        http.perform(get("/api/tipos")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.codigo").value("ERROR_INTERNO"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("credencial"))));
    }
}
