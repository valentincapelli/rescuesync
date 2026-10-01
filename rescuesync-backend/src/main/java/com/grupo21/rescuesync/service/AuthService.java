package com.grupo21.rescuesync.service;

import com.grupo21.rescuesync.dto.LoginRequest;
import com.grupo21.rescuesync.dto.LoginResponse;
import com.grupo21.rescuesync.model.Usuario;
import com.grupo21.rescuesync.repository.UsuarioRepository;
import com.grupo21.rescuesync.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UsuarioRepository usuarioRepository;
    private final JwtService jwtService;

    public LoginResponse login(LoginRequest request) {

        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(
                request.email(),
                request.password()
            )
        );

        Usuario usuario = usuarioRepository.findByEmail(request.email())
                .orElseThrow();

        String token = jwtService.generarToken(usuario);

        return new LoginResponse(token);
    }
}