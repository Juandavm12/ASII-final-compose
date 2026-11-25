package calendario;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ArquitecturaTest {
    @Test
    void nucleoYAplicacionNoImportanFrameworksNiAdaptadores() throws IOException {
        Path raiz = Path.of("src/main/java/calendario");
        for (String capa : List.of("core", "aplicacion")) {
            try (var archivos = Files.walk(raiz.resolve(capa))) {
                var fuentes = archivos.filter(p -> p.toString().endsWith(".java")).toList();
                assertFalse(fuentes.isEmpty(), "Faltan fuentes de " + capa);
                for (Path fuente : fuentes) {
                    String codigo = Files.readString(fuente);
                    for (String prohibido : List.of("import org.springframework", "import jakarta.persistence",
                            "import java.net", "import java.sql", "import javax.sql",
                            "import calendario.infraestructura")) {
                        assertFalse(codigo.contains(prohibido), fuente + " depende de " + prohibido);
                    }
                }
            }
        }
    }
}
