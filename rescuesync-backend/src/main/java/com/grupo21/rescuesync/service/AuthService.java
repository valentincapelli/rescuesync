package com.grupo21.rescuesync.service;

import com.grupo21.rescuesync.dto.LoginRequest;
import com.grupo21.rescuesync.dto.LoginResponse;
import com.grupo21.rescuesync.exception.ResourceNotFoundException;
import com.grupo21.rescuesync.model.AuthResult;
import com.grupo21.rescuesync.model.Usuario;
import com.grupo21.rescuesync.repository.UsuarioRepository;
import com.grupo21.rescuesync.security.JwtService;
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
                        request.email(),
                        request.password()
                )
        );

        Usuario usuario = usuarioRepository
                .findByEmail(request.email())
                .orElseThrow(() ->
                        new UsernameNotFoundException(
                                "Usuario no encontrado"
                        )
                );

        String token = jwtService.generarToken(usuario);

        return new AuthResult(
                token,
                usuario.getEmail(),
                usuario.getRol()
        );
    }

    public LoginResponse me(String email) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Usuario no encontrado")
                );

        return new LoginResponse(
                usuario.getEmail(),
                usuario.getRol()
        );
    }
}