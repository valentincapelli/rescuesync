package com.grupo21.sistemanacional.config;

import com.grupo21.sistemanacional.dto.ApiError;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Documentación OpenAPI/Swagger (E3-11).
 * <ul>
 *   <li>Swagger UI: {@code /swagger-ui.html}</li>
 *   <li>Contrato OpenAPI: {@code /v3/api-docs} (JSON) y {@code /v3/api-docs.yaml} (YAML)</li>
 * </ul>
 * Lo que es igual para todos los endpoints se completa acá y no en cada controller: el esquema de
 * seguridad, los roles permitidos (se leen de {@code @PreAuthorize}) y las respuestas de error
 * 400/401/403/404 con el formato {@link ApiError}. Lo específico de cada servicio va en su controller.
 */
@Configuration
public class OpenApiConfig {

    /** Nombre del esquema de seguridad en el contrato; los controllers públicos lo quitan con @SecurityRequirements. */
    public static final String COOKIE_AUTH = "cookieAuth";

    private static final String REF_API_ERROR = "#/components/schemas/ApiError";
    private static final Pattern ROL_EN_EXPRESION = Pattern.compile("'([A-Z_]+)'");

    private static final String DESCRIPCION = """
            API REST del **Sistema Nacional de Gestión de Recursos y Riesgos**, alojada en el Cloud y \
            consumida por el motor de Bonita (y por el administrador y las ONGs). DSSD 2026 - Grupo 21.

            **Servicios**
            - *Autenticación y cuentas*: login con JWT y registro (onboarding) de ONGs.
            - *Catálogo y consulta*: ONGs habilitadas, certificaciones y disponibilidad global de recursos.
            - *Validación por niveles de competencia*: devuelve capacidades habilitadas y riesgo máximo permitido \
            (no rechaza de forma binaria).
            - *Compromiso de recursos*: reserva transaccional con bloqueo (`SELECT ... FOR UPDATE`) para evitar \
            sobreasignaciones. Responde 409 si no alcanza el stock.
            - *Liberación y cierre*: devuelve el stock comprometido al cerrar la oferta o la emergencia.

            **Autenticación.** `POST /api/auth/login` devuelve el JWT en una cookie `HttpOnly` llamada \
            `access_token` (el cuerpo de la respuesta solo trae usuario y rol). Todos los demás endpoints, salvo \
            los marcados como públicos, requieren enviar esa cookie. En Swagger UI alcanza con ejecutar el login: \
            el navegador guarda la cookie y la reenvía en las siguientes pruebas.

            **Roles.** `ADMIN` habilita y certifica ONGs; `ONG` gestiona su propio inventario; \
            `SERVICIO` es el usuario técnico de Bonita (valida, compromete y libera).

            **Errores.** Todos los errores tienen el mismo formato (`ApiError`): `timestamp`, `status`, `error`, \
            `message`, `path` y, en los 400 de validación, `fieldErrors` por campo.

            **Identificadores externos.** `emergenciaRef` y `ofertaRef` son los ids de RescueSync; esta API no \
            guarda emergencias ni ofertas, solo los referencia.
            """;

    @Bean
    public OpenAPI sistemaNacionalOpenApi(@Value("${app.version:dev}") String version) {
        return new OpenAPI()
                .info(new Info()
                        .title("Sistema Nacional de Gestión de Recursos y Riesgos")
                        .description(DESCRIPCION)
                        .version(version)
                        .contact(new Contact().name("DSSD 2026 - Grupo 21")))
                .components(new Components().addSecuritySchemes(COOKIE_AUTH, new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.COOKIE)
                        .name("access_token")
                        .description("JWT en cookie HttpOnly `access_token`, la fija `POST /api/auth/login`. "
                                + "No se carga a mano: se obtiene ejecutando el login.")))
                .addSecurityItem(new SecurityRequirement().addList(COOKIE_AUTH))
                .tags(List.of(
                        tag("Info", "Verificación de que la API está arriba."),
                        tag("Autenticación", "Login con JWT, logout y datos de la sesión."),
                        tag("ONGs", "Onboarding y consulta de ONGs."),
                        tag("Certificaciones", "Certificaciones por tipo de recurso y validación por niveles de competencia."),
                        tag("Recursos", "Inventario de cada ONG y disponibilidad global."),
                        tag("Compromisos", "Compromiso transaccional de recursos con control de concurrencia."),
                        tag("Liberación", "Liberación de recursos comprometidos y cierre.")));
    }

    /** Registra el schema ApiError, al que apuntan las respuestas de error comunes. */
    @Bean
    @SuppressWarnings({"rawtypes", "unchecked"})
    public OpenApiCustomizer esquemaApiError() {
        return openApi -> ModelConverters.getInstance().readAll(ApiError.class)
                .forEach((nombre, schema) -> openApi.getComponents().addSchemas(nombre, (Schema) schema));
    }

    /** Completa en cada operación los roles permitidos y las respuestas de error que aplican a todas. */
    @Bean
    public OperationCustomizer rolesYErroresComunes() {
        return (operation, handlerMethod) -> {
            // Público = el controller lo marcó con @SecurityRequirements vacío (sin cookie).
            SecurityRequirements sinSeguridad = handlerMethod.getMethodAnnotation(SecurityRequirements.class);
            boolean publica = (sinSeguridad != null && sinSeguridad.value().length == 0)
                    || (operation.getSecurity() != null && operation.getSecurity().isEmpty());
            if (publica) {
                operation.setSecurity(new ArrayList<>());
            }
            PreAuthorize preAuthorize = handlerMethod.getMethodAnnotation(PreAuthorize.class);

            agregarAcceso(operation, publica, preAuthorize);

            boolean tieneBody = false;
            for (MethodParameter parametro : handlerMethod.getMethodParameters()) {
                if (parametro.hasParameterAnnotation(Valid.class)) {
                    tieneBody = true;
                    break;
                }
            }
            boolean tienePathVariable = operation.getParameters() != null && operation.getParameters().stream()
                    .anyMatch(p -> "path".equals(p.getIn()));

            if (tieneBody) {
                agregarError(operation, "400", "Datos inválidos: el cuerpo no es un JSON válido o falla la validación "
                        + "(el detalle por campo viene en `fieldErrors`).");
            }
            if (!publica) {
                agregarError(operation, "401", "Sin sesión, token inválido o token vencido.");
            }
            if (!publica && (preAuthorize != null || tienePathVariable)) {
                agregarError(operation, "403", "El rol del usuario no alcanza para esta operación"
                        + (tienePathVariable ? ", o es una ONG accediendo a datos de otra." : "."));
            }
            if (tienePathVariable) {
                agregarError(operation, "404", "No existe el recurso indicado en la ruta.");
            }
            return operation;
        };
    }

    private static Tag tag(String nombre, String descripcion) {
        return new Tag().name(nombre).description(descripcion);
    }

    private static void agregarAcceso(Operation operation, boolean publica, PreAuthorize preAuthorize) {
        String acceso;
        if (publica) {
            acceso = "**Acceso:** público, no requiere sesión.";
        } else {
            List<String> roles = new ArrayList<>();
            if (preAuthorize != null) {
                Matcher matcher = ROL_EN_EXPRESION.matcher(preAuthorize.value());
                while (matcher.find()) {
                    roles.add(matcher.group(1));
                }
            }
            acceso = roles.isEmpty()
                    ? "**Acceso:** cualquier usuario autenticado."
                    : "**Acceso:** requiere rol " + String.join(" o ", roles) + ".";
        }
        String descripcion = operation.getDescription();
        operation.setDescription(descripcion == null || descripcion.isBlank()
                ? acceso
                : descripcion + "\n\n" + acceso);
    }

    private static void agregarError(Operation operation, String codigo, String descripcion) {
        if (operation.getResponses() == null) {
            operation.setResponses(new ApiResponses());
        }
        if (operation.getResponses().containsKey(codigo)) {
            return;
        }
        operation.getResponses().addApiResponse(codigo, new ApiResponse()
                .description(descripcion)
                .content(new Content().addMediaType("application/json",
                        new MediaType().schema(new Schema<Object>().$ref(REF_API_ERROR)))));
    }
}
