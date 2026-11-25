package calendario.infraestructura.web;

import calendario.aplicacion.CalendarioServicio;
import calendario.core.modelo.CodigoError;
import calendario.core.modelo.ErrorNegocio;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/calendario")
public class CalendarioController {
    private final CalendarioServicio servicio;

    public CalendarioController(CalendarioServicio servicio) {
        this.servicio = servicio;
    }

    @PostMapping("/generar/{anio}")
    public boolean generar(@PathVariable int anio, @RequestBody(required = false) String cuerpo) {
        if (cuerpo != null && !cuerpo.isBlank()) {
            throw new ErrorNegocio(CodigoError.SOLICITUD_INVALIDA, "La generacion no recibe cuerpo.");
        }
        return servicio.generar(anio);
    }

    @GetMapping("/listar/{anio}")
    public List<Respuestas.DiaDto> listar(@PathVariable int anio) {
        return servicio.listar(anio).stream().map(Respuestas.DiaDto::desde).toList();
    }
}
