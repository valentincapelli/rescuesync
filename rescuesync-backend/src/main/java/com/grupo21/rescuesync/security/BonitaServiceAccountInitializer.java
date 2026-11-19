package com.grupo21.rescuesync.security;

import com.grupo21.rescuesync.model.Rol;
import com.grupo21.rescuesync.model.Usuario;
import com.grupo21.rescuesync.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class BonitaServiceAccountInitializer implements ApplicationRunner {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${RESCUESYNC_BONITA_SERVICE_EMAIL:}")
    private String email;

    @Value("${RESCUESYNC_BONITA_SERVICE_PASSWORD:}")
    private String password;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        boolean tieneEmail = StringUtils.hasText(email);
        boolean tienePassword = StringUtils.hasText(password);
        if (!tieneEmail && !tienePassword) {
            return;
        }
        if (!tieneEmail || !tienePassword) {
            throw new IllegalStateException(
                    "Configurar juntas RESCUESYNC_BONITA_SERVICE_EMAIL y RESCUESYNC_BONITA_SERVICE_PASSWORD");
        }

        String emailNormalizado = email.trim();
        Usuario usuario = usuarioRepository.findByEmail(emailNormalizado).orElseGet(() -> {
            Usuario nuevo = new Usuario();
            nuevo.setEmail(emailNormalizado);
            nuevo.setRol(Rol.SERVICIO_BONITA);
            return nuevo;
        });

        if (usuario.getRol() != Rol.SERVICIO_BONITA
                || usuario.getMunicipio() != null || usuario.getOng() != null) {
            throw new IllegalStateException("La identidad técnica configurada ya pertenece a otro usuario");
        }

        if (usuario.getPasswordHash() == null
                || !passwordEncoder.matches(password, usuario.getPasswordHash())) {
            usuario.setPasswordHash(passwordEncoder.encode(password));
            usuarioRepository.save(usuario);
        }
    }
}
