package com.grupo21.rescuesync.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grupo21.rescuesync.dto.ApiError;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UsuarioDetailsService usuarioDetailsService;
    private final ObjectMapper objectMapper;

    @Override
        protected void doFilterInternal(
                HttpServletRequest request,
                HttpServletResponse response,
                FilterChain filterChain
        ) throws ServletException, IOException {

        String token = null;

        Cookie[] cookies = request.getCookies();

        if (cookies != null) {
                for (Cookie cookie : cookies) {
                if ("access_token".equals(cookie.getName())) {
                        token = cookie.getValue();
                        break;
                }
                }
        }

        if (token == null) {
                filterChain.doFilter(request, response);
                return;
        }

        try {
                Claims claims = jwtService.extraerClaims(token);

                String email = claims.getSubject();

                if (email != null &&
                        SecurityContextHolder
                                .getContext()
                                .getAuthentication() == null) {

                UserDetails userDetails =
                        usuarioDetailsService
                                .loadUserByUsername(email);

                if (jwtService.validarClaims(claims, userDetails)) {

                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(
                                        userDetails,
                                        null,
                                        userDetails.getAuthorities()
                                );

                        authentication.setDetails(
                                new WebAuthenticationDetailsSource()
                                        .buildDetails(request)
                        );

                        SecurityContextHolder
                                .getContext()
                                .setAuthentication(authentication);
                }
                }

        } catch (ExpiredJwtException ex) {
                responder401(response, request, "Token expirado");
                return;

        } catch (JwtException | IllegalArgumentException ex) {
                responder401(response, request, "Token inválido");
                return;

        } catch (UsernameNotFoundException ex) {
                responder401(response, request, "Token inválido");
                return;
        }

        filterChain.doFilter(request, response);
        }

    private void responder401(
            HttpServletResponse response,
            HttpServletRequest request,
            String message
    ) throws IOException {

        ApiError error = ApiError.of(
                HttpServletResponse.SC_UNAUTHORIZED,
                "Unauthorized",
                message,
                request.getRequestURI()
        );

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        objectMapper.writeValue(
                response.getWriter(),
                error
        );
    }
}