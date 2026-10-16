package com.grupo21.sistemanacional.controller;

import com.grupo21.sistemanacional.dto.LoginRequest;
import com.grupo21.sistemanacional.dto.LoginResponse;
import com.grupo21.sistemanacional.model.AuthResult;
import com.grupo21.sistemanacional.service.AuthService;

import io.swagger.v3.oas.annotations.Operation;
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

    @Operation(summary = "Iniciar sesión y obtener un token JWT")
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request
    ) {
        AuthResult result = authService.login(request);

        ResponseCookie cookie = ResponseCookie
                .from("access_token", result.token())
                .httpOnly(true)
                .secure(false)
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

    @Operation(summary = "Cerrar sesión y eliminar el token JWT")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {

        ResponseCookie cookie = ResponseCookie
                .from("access_token", "")
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path("/")
                .maxAge(0)
                .build();

        return ResponseEntity
                .noContent()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .build();
    }

    @Operation(summary = "Obtener información del usuario autenticado")
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