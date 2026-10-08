package com.academy.paybridge.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String SCHEME = "apiKey";

    @Bean
    OpenAPI payBridgeOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("PayBridge API")
                        .version("v1")
                        .description("Sandbox payments backend. Start with POST /api/v1/customers/register, "
                                + "copy the apiKey from the response, click Authorize and paste it. "
                                + "Then open an account, fund it from the Sandbox section, and transfer."))
                .components(new Components().addSecuritySchemes(SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-API-Key")))
                .addSecurityItem(new SecurityRequirement().addList(SCHEME));
    }
}