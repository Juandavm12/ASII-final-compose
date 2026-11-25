package calendario.infraestructura.configuracion;

import calendario.aplicacion.CalendarioServicio;
import calendario.aplicacion.TipoServicio;
import calendario.core.puertos.CalendarioRepositorio;
import calendario.core.puertos.FestivosProveedor;
import calendario.core.puertos.TipoRepositorio;
import calendario.core.puertos.UnidadDeTrabajo;
import calendario.infraestructura.http.FestivosHttpCliente;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
public class AplicacionConfiguracion {
    @Bean(destroyMethod = "close")
    FestivosHttpCliente festivosProveedor(@Value("${festivos.url}") URI url,
                                         @Value("${festivos.timeout}") Duration timeout,
                                         ObjectMapper json) {
        return new FestivosHttpCliente(url, timeout, json);
    }

    @Bean
    CalendarioServicio calendarioServicio(CalendarioRepositorio calendarios, TipoRepositorio tipos,
                                          FestivosProveedor festivos, UnidadDeTrabajo trabajo) {
        return new CalendarioServicio(calendarios, tipos, festivos, trabajo);
    }

    @Bean
    TipoServicio tipoServicio(TipoRepositorio tipos, UnidadDeTrabajo trabajo) {
        return new TipoServicio(tipos, trabajo);
    }
}
