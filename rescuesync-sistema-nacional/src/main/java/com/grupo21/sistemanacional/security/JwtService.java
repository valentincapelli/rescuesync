package com.grupo21.sistemanacional.security;

import com.grupo21.sistemanacional.model.Rol;
import com.grupo21.sistemanacional.model.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey secretKey;
    private final long expiration;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration}") long expiration
    ) {
        this.secretKey = Keys.hmacShaKeyFor(
                secret.getBytes(StandardCharsets.UTF_8)
        );
        this.expiration = expiration;
    }

    public String generarToken(Usuario usuario) {
        Date ahora = new Date();
        Date expiracion = new Date(ahora.getTime() + expiration);

        var jwt = Jwts.builder()
                .subject(usuario.getUsername())
                .claim("rol", usuario.getRol().name());

        if (usuario.getRol() == Rol.ONG) {
            jwt.claim("ong_id", usuario.getOng().getId());
        }

        return jwt
                .issuedAt(ahora)
                .expiration(expiracion)
                .signWith(secretKey)
                .compact();
    }

    public Claims extraerClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean validarToken(String token, UserDetails userDetails) {
        Claims claims = extraerClaims(token);

        return claims.getSubject().equals(userDetails.getUsername())
                && claims.getExpiration().after(new Date());
    }

    public boolean validarClaims(
            Claims claims,
            UserDetails userDetails
    ) {
        return claims.getSubject().equals(userDetails.getUsername());
    }
}