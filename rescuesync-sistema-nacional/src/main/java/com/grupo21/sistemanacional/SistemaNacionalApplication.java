package com.grupo21.sistemanacional;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * API del Sistema Nacional de Gestión de Recursos y Riesgos.
 * Es un servicio aparte de RescueSync, con su propia base de datos, y la consume Bonita.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class SistemaNacionalApplication {

    public static void main(String[] args) {
        SpringApplication.run(SistemaNacionalApplication.class, args);
    }
}
