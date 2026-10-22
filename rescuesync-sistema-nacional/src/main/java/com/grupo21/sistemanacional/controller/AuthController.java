package com.grupo21.sistemanacional.controller;

import com.grupo21.sistemanacional.dto.LoginRequest;
import com.grupo21.sistemanacional.dto.LoginResponse;
import com.grupo21.sistemanacional.model.AuthResult;
import com.grupo21.sistemanacional.service.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

@Tag(name = "Autenticación")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    @Value("${app.auth.cookie-secure}")
    private boolean cookieSecure;

    @Operation(summary = "Iniciar sesión y obtener un token JWT",
            description = "Valida usuario y contraseña. El JWT **no** viaja en el cuerpo: se entrega en la cookie "
                    + "`access_token` (HttpOnly, SameSite=Lax), que hay que reenviar en las demás llamadas. "
                    + "El cuerpo devuelve solo el usuario y su rol. Bonita debe conservar la cookie del `Set-Cookie` "
                    + "y enviarla en cada petición.")
    @SecurityRequirements
    @ApiResponse(responseCode = "200", description = "Sesión iniciada. La cookie `access_token` llega en el header `Set-Cookie`.",
            headers = @Header(name = "Set-Cookie", description = "access_token=<jwt>; Path=/; HttpOnly; SameSite=Lax "
                    + "(más Secure cuando COOKIE_SECURE=true, como en Render)", schema = @Schema(type = "string")),
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = LoginResponse.class),
                    examples = @ExampleObject(value = "{\"username\": \"bonita\", \"rol\": \"SERVICIO\"}")))
    @ApiResponse(responseCode = "401", description = "Usuario o contraseña incorrectos.",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(ref = "#/components/schemas/ApiError")))
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"username\": \"bonita\", \"password\": \"secreto123\"}")))
            @RequestBody LoginRequest request
    ) {
        AuthResult result = authService.login(request);

        ResponseCookie cookie = ResponseCookie
                .from("access_token", result.token())
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofMillis(jwtExpiration))
                .build();

        LoginResponse response = new LoginResponse(
                result.username(),
                result.rol()
        );

        return ResponseEntity
                .ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(response);
    }

    @Operation(summary = "Cerrar sesión y eliminar el token JWT",
            description = "Vence la cookie `access_token`. Es idempotente: también responde 204 sin sesión.")
    @SecurityRequirements
    @ApiResponse(responseCode = "204", description = "Cookie eliminada.",
            headers = @Header(name = "Set-Cookie", description = "access_token vacío con Max-Age=0",
                    schema = @Schema(type = "string")))
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {

        ResponseCookie cookie = ResponseCookie
                .from("access_token", "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/")
                .maxAge(0)
                .build();

        return ResponseEntity
                .noContent()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .build();
    }

    @Operation(summary = "Obtener información del usuario autenticado",
            description = "Devuelve el usuario y el rol de la sesión actual. Sirve para comprobar que la cookie sigue vigente.")
    @GetMapping("/me")
    public ResponseEntity<LoginResponse> me(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        LoginResponse response = authService.me(
                userDetails.getUsername()
        );

        return ResponseEntity.ok(response);
    }
}
