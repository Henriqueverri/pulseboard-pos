package dev.henriqueverri.pos.shared;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.math.BigDecimal;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

  public static final String BEARER_SCHEME = "bearerAuth";

  static {
    // Mirrors MoneyJacksonModule: every BigDecimal is money and travels as a decimal string.
    SpringDocUtils.getConfig()
        .replaceWithSchema(BigDecimal.class, new StringSchema().format("decimal").example("79.90"));
  }

  @Bean
  OpenAPI posOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("PulseBoard POS API")
                .version("v1")
                .description(
                    "API do ponto de venda. Faça login em `POST /api/auth/login` e use o"
                        + " `access_token` em Authorize. Dinheiro trafega como string decimal com"
                        + " duas casas; erros seguem ProblemDetail (RFC 9457) com a propriedade"
                        + " `code`."))
        .components(
            new Components()
                .addSecuritySchemes(
                    BEARER_SCHEME,
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
        .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
  }
}
