package calendario.infraestructura.web;

import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class JsonConfiguracion {
    @Bean
    Jackson2ObjectMapperBuilderCustomizer textoSinCoercion() {
        return builder -> builder.postConfigurer(mapper -> {
            var texto = mapper.coercionConfigFor(LogicalType.Textual);
            texto.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail);
            texto.setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
            texto.setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
        });
    }
}
