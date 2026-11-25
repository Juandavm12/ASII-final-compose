package calendario.infraestructura.web;

import calendario.core.modelo.ErrorNegocio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExcepcionesHandler extends ResponseEntityExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ApiExcepcionesHandler.class);

    @ExceptionHandler(ErrorNegocio.class)
    ResponseEntity<Respuestas.ErrorDto> negocio(ErrorNegocio error) {
        int estado = switch (error.codigo()) {
            case SOLICITUD_INVALIDA -> 400;
            case TIPO_NO_ENCONTRADO -> 404;
            case TIPO_EXISTENTE, TIPO_EN_USO, TIPOS_REQUERIDOS_AUSENTES -> 409;
            case RESPUESTA_FESTIVOS_INVALIDA -> 502;
            case FESTIVOS_NO_DISPONIBLE -> 503;
            case TIEMPO_FESTIVOS_AGOTADO -> 504;
        };
        return ResponseEntity.status(estado).body(new Respuestas.ErrorDto(error.codigo().name(), error.getMessage()));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception error, Object body, HttpHeaders headers,
                                                              HttpStatusCode status, WebRequest request) {
        return new ResponseEntity<>(new Respuestas.ErrorDto("SOLICITUD_INVALIDA",
                "La solicitud no corresponde al contrato de la API."), headers, status);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Respuestas.ErrorDto> inesperado(Exception error) {
        // Registrar el tipo evita exponer URLs, SQL o credenciales de excepciones externas.
        LOG.error("Fallo interno de tipo {}", error.getClass().getName());
        return ResponseEntity.internalServerError().body(new Respuestas.ErrorDto("ERROR_INTERNO",
                "No fue posible completar la operacion."));
    }
}
