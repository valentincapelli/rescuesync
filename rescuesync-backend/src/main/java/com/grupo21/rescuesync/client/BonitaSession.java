package com.grupo21.rescuesync.client;

/**
 * Sesión autenticada contra el engine de Bonita: el par de valores que hay que
 * reenviar en cada llamada posterior al login (ver CSRF security de Bonita).
 *
 * jSessionId  -> cookie JSESSIONID, identifica la sesión HTTP en el engine.
 * apiToken    -> cookie X-Bonita-API-Token, token anti-CSRF. Además de ir como
 *                cookie, Bonita exige mandarlo también como header en todo
 *                request que no sea GET.
 */
record BonitaSession(String jSessionId, String apiToken) {
}
