package calendario.infraestructura.web;

import calendario.aplicacion.TipoServicio;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/tipos")
public class TipoController {
    private final TipoServicio servicio;

    public TipoController(TipoServicio servicio) {
        this.servicio = servicio;
    }

    public record CrearTipo(Integer id, String tipo) { }
    public record ActualizarTipo(String tipo) { }

    @PostMapping
    public ResponseEntity<Respuestas.TipoDto> crear(@RequestBody CrearTipo solicitud) {
        var tipo = servicio.crear(solicitud.id(), solicitud.tipo());
        return ResponseEntity.created(URI.create("/api/tipos/" + tipo.id())).body(Respuestas.TipoDto.desde(tipo));
    }

    @GetMapping
    public List<Respuestas.TipoDto> listar() {
        return servicio.listar().stream().map(Respuestas.TipoDto::desde).toList();
    }

    @GetMapping("/{id}")
    public Respuestas.TipoDto obtener(@PathVariable Integer id) {
        return Respuestas.TipoDto.desde(servicio.obtener(id));
    }

    @PutMapping("/{id}")
    public Respuestas.TipoDto actualizar(@PathVariable Integer id, @RequestBody ActualizarTipo solicitud) {
        return Respuestas.TipoDto.desde(servicio.actualizar(id, solicitud.tipo()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Integer id) {
        servicio.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
