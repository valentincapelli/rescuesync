package com.grupo21.sistemanacional.service;

import com.grupo21.sistemanacional.dto.LoginRequest;
import com.grupo21.sistemanacional.dto.LoginResponse;
import com.grupo21.sistemanacional.exception.ResourceNotFoundException;
import com.grupo21.sistemanacional.model.AuthResult;
import com.grupo21.sistemanacional.model.Usuario;
import com.grupo21.sistemanacional.repository.UsuarioRepository;
import com.grupo21.sistemanacional.security.JwtService;
import lombok.RequiredArgsConstructor;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UsuarioRepository usuarioRepository;
    private final JwtService jwtService;

    public AuthResult login(LoginRequest request) {

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.username(),
                        request.password()
                )
        );

        Usuario usuario = usuarioRepository
                .findByUsername(request.username())
                .orElseThrow(() ->
                        new UsernameNotFoundException(
                                "Usuario no encontrado"
                        )
                );

        String token = jwtService.generarToken(usuario);

        return new AuthResult(
                token,
                usuario.getUsername(),
                usuario.getRol()
        );
    }

    public LoginResponse me(String username) {
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Usuario no encontrado")
                );

        return new LoginResponse(
                usuario.getUsername(),
                usuario.getRol()
        );
    }
}