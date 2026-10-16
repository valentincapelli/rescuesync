package com.grupo21.sistemanacional.dto;

import java.time.Instant;

public record InfoResponse(String application, String version, String status, Instant serverTime) {
}
