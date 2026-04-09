package ru.aritmos.integration.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Глобальная конфигурация OpenAPI/Swagger UI документации.
 */
@OpenAPIDefinition(
        info = @Info(
                title = "Integration API",
                version = "v1",
                description = "Интеграционный API для маршрутизации запросов, event-driven синхронизации и programmable-интеграций.",
                contact = @Contact(name = "Integration API Team"),
                license = @License(name = "Proprietary")
        ),
        servers = {
                @Server(url = "/", description = "Текущий адрес сервиса"),
                @Server(url = "http://localhost:8080", description = "Локальный запуск")
        },
        tags = {
                @Tag(name = "Integration Gateway", description = "REST-операции очередей/визитов/состояния отделений"),
                @Tag(name = "Eventing", description = "Ingest/replay/DLQ/snapshot интерфейсы event pipeline"),
                @Tag(name = "Programmable API", description = "Управление и запуск программируемых endpoint-ов и Groovy-скриптов"),
                @Tag(name = "Internal Auth", description = "Получение internal JWT для технических клиентов"),
                @Tag(name = "Health", description = "Liveness/readiness проверки и сводное состояние системы")
        },
        security = {
                @SecurityRequirement(name = "apiKeyAuth"),
                @SecurityRequirement(name = "bearerAuth")
        }
)
public final class OpenApiDocumentationConfiguration {

    private OpenApiDocumentationConfiguration() {
    }
}

