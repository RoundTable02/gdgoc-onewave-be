package gdgoc.onewave.connectable.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Connectable API Specification")
                        .description("API specification for GDGOC Onewave Connectable project.")
                        .version("v0.0.1"));
    }
}