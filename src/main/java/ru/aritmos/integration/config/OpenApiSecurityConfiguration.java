package ru.aritmos.integration.config;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;

/**
 * Security схемы для Swagger UI.
 */
@SecurityScheme(
        name = "apiKeyAuth",
        type = SecuritySchemeType.APIKEY,
        in = SecuritySchemeIn.HEADER,
        paramName = "X-API-KEY",
        description = "API-ключ для режима security-mode=API_KEY или HYBRID."
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "JWT токен для режимов INTERNAL/KEYCLOAK/HYBRID."
)
public final class OpenApiSecurityConfiguration {

    private OpenApiSecurityConfiguration() {
    }
}

