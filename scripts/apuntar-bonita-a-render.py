#!/usr/bin/env python3
"""Apunta los casos de Bonita al Sistema Nacional hosteado (Render).

El proceso RescueSync 1.2 inicia la variable `sistemaNacionalBaseUrl` en http://localhost:8082 y la
pantalla no permite cambiarla. Este script vigila los casos abiertos y, a cada uno cuya variable no
apunte a la URL pedida, se la corrige por la API REST de Bonita. Como el conector la lee recién en
"Evaluar ofertas" (al vencer la convocatoria), alcanza con que el script esté corriendo durante la demo.

Uso (dejarlo abierto en una terminal mientras dure la demo):
    python scripts/apuntar-bonita-a-render.py            # vigila hasta Ctrl+C
    python scripts/apuntar-bonita-a-render.py --una-vez  # corrige los casos actuales y sale

Opciones: --url (default Render), --bonita (default http://localhost:8080/bonita),
          --usuario/--clave (default walter.bates / bpm), --intervalo (segundos, default 3).
Solo usa la biblioteca estándar. No toca el proceso ni los casos archivados.
"""

import argparse
import json
import time
import urllib.error
import urllib.request
from http.cookiejar import CookieJar
from urllib.parse import urlencode

RENDER = "https://rescuesync-sistema-nacional-2qwh.onrender.com"
VARIABLE = "sistemaNacionalBaseUrl"


class Bonita:
    def __init__(self, base, usuario, clave):
        self.base = base.rstrip("/")
        self.jar = CookieJar()
        self.http = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.jar))
        self.usuario, self.clave = usuario, clave
        self.token = None

    def login(self):
        data = urlencode({"username": self.usuario, "password": self.clave, "redirect": "false"}).encode()
        self.http.open(urllib.request.Request(self.base + "/loginservice", data=data), timeout=15).read()
        self.token = next((c.value for c in self.jar if c.name == "X-Bonita-API-Token"), None)
        if not self.token:
            raise RuntimeError("Bonita no devolvió X-Bonita-API-Token")

    def call(self, metodo, ruta, cuerpo=None, reintentar=True):
        headers = {"X-Bonita-API-Token": self.token or "", "Content-Type": "application/json"}
        data = json.dumps(cuerpo).encode() if cuerpo is not None else None
        req = urllib.request.Request(self.base + ruta, data=data, method=metodo, headers=headers)
        try:
            with self.http.open(req, timeout=15) as r:
                texto = r.read().decode()
                return json.loads(texto) if texto else None
        except urllib.error.HTTPError as e:
            if e.code in (401, 403) and reintentar:
                self.login()
                return self.call(metodo, ruta, cuerpo, reintentar=False)
            raise


def corregir(bonita, url):
    """Devuelve la lista de casos corregidos en esta pasada."""
    corregidos = []
    for caso in bonita.call("GET", "/API/bpm/case?p=0&c=200") or []:
        cid = caso["id"]
        try:
            actual = bonita.call("GET", f"/API/bpm/caseVariable/{cid}/{VARIABLE}")["value"]
        except urllib.error.HTTPError as e:
            if e.code == 404:  # caso de otro proceso, sin la variable
                continue
            raise
        if actual.rstrip("/") != url.rstrip("/"):
            bonita.call("PUT", f"/API/bpm/caseVariable/{cid}/{VARIABLE}", {"type": "java.lang.String", "value": url})
            corregidos.append((cid, actual))
    return corregidos


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--url", default=RENDER)
    ap.add_argument("--bonita", default="http://localhost:8080/bonita")
    ap.add_argument("--usuario", default="walter.bates")
    ap.add_argument("--clave", default="bpm")
    ap.add_argument("--intervalo", type=float, default=3)
    ap.add_argument("--una-vez", action="store_true")
    a = ap.parse_args()

    bonita = Bonita(a.bonita, a.usuario, a.clave)
    bonita.login()
    print(f"Vigilando casos de Bonita; {VARIABLE} -> {a.url}", flush=True)
    while True:
        try:
            for cid, antes in corregir(bonita, a.url):
                print(f"{time.strftime('%H:%M:%S')} caso {cid}: {antes} -> {a.url}", flush=True)
        except (urllib.error.URLError, OSError, RuntimeError) as e:
            print(f"{time.strftime('%H:%M:%S')} aviso: {e}", flush=True)
        if a.una_vez:
            break
        time.sleep(a.intervalo)


if __name__ == "__main__":
    main()
