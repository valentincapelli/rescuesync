package com.grupo21.rescuesync.controller;

import com.grupo21.rescuesync.dto.NotificacionResponse;
import com.grupo21.rescuesync.service.NotificacionService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notificaciones")
@RequiredArgsConstructor
public class NotificacionController {

    private final NotificacionService notificacionService;

    @GetMapping
    public ResponseEntity<List<NotificacionResponse>> obtenerNotificaciones(
            @AuthenticationPrincipal UserDetails userDetails
    ) {

        String email = userDetails.getUsername();

        return ResponseEntity.ok(
                notificacionService.obtenerNotificaciones(email)
        );
    }

    @GetMapping("/no-leidas/count")
    public ResponseEntity<Long> contarNoLeidas(
            @AuthenticationPrincipal UserDetails userDetails
    ) {

        String email = userDetails.getUsername();

        return ResponseEntity.ok(
                notificacionService.contarNoLeidas(email)
        );
    }

    @PatchMapping("/{notificacionId}/leida")
    public ResponseEntity<Void> marcarComoLeida(
            @PathVariable Long notificacionId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {

        String email = userDetails.getUsername();

        notificacionService.marcarComoLeida(
                notificacionId,
                email
        );

        return ResponseEntity.noContent().build();
    }
}