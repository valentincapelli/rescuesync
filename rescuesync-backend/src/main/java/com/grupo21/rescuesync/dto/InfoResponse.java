package com.grupo21.rescuesync.dto;

import java.time.Instant;

public record InfoResponse(String application, String version, String status, Instant serverTime) {
}
