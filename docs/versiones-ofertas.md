# Gestión de versiones de ofertas

Fecha: 20/11/2026. Rama `feature/versiones-ofertas`.

## Qué pide el enunciado

Punto 2 (Aplicación Web), dentro de las especificaciones obligatorias:

> **Gestión de Versiones de Ofertas:** Pantallas que permitan a las ONGs editar o actualizar el detalle de sus recursos ofertados dentro de la ventana de tiempo permitida, **manteniendo trazabilidad en el backend**.

La edición dentro de la ventana de tiempo ya existía. Faltaba la trazabilidad: `OfertaService.editar()` pisaba la cantidad y las observaciones sin dejar registro.

## Qué se hizo

| Parte | Cambio |
|---|---|
| Modelo | Entidad `RevisionOferta`, tabla `revisiones_oferta`: oferta, autor (usuario), fecha (`created_at`), cantidad y observaciones anteriores y nuevas. Las filas no se editan ni se borran |
| Edición | `editar()` guarda una revisión **antes** de pisar los valores. Si los datos no cambian, no la guarda. Si la edición se rechaza (fuera de término, otra ONG, oferta ya evaluada), tampoco |
| Concurrencia | `editar()` bloquea la fila de la oferta (`PESSIMISTIC_WRITE`) para que dos ediciones simultáneas no registren el mismo valor anterior |
| API | `GET /api/lotes/{loteId}/ofertas/{ofertaId}/versiones` |
| Frontend | En "Mis ofertas", el botón **Ver historial de versiones** de cada oferta muestra las versiones, de la más nueva a la más vieja |
| Base existente | `scripts/revisiones_oferta.sql`, solo si no se usa `ddl-auto=update` (el default local sí lo usa y crea la tabla solo) |

## Contrato del endpoint

Permiso `CONSULTAR_OFERTAS`. La ONG solo ve sus ofertas (otra ONG recibe 409). El Centro Coordinador, que no tiene ONG, ve las de cualquiera. Lote y oferta que no coinciden: 404.

```json
[
  { "numero": 1, "fecha": "2026-11-20T10:00:00Z", "autorEmail": null,
    "cantidadOfrecida": 100, "observaciones": "Original",
    "cantidadAnterior": null, "observacionesAnteriores": null },
  { "numero": 2, "fecha": "2026-11-20T11:30:00Z", "autorEmail": "ong@ejemplo.org",
    "cantidadOfrecida": 80, "observaciones": "Menos stock",
    "cantidadAnterior": 100, "observacionesAnteriores": "Original" }
]
```

La versión 1 es el alta. No tiene autor porque la oferta nunca guardó quién la cargó, y sus valores salen de los "anteriores" de la primera edición. Las ofertas creadas antes de este cambio muestran solo esa versión hasta que se editen.

## Pruebas

`VersionesOfertaIntegrationTests` (H2 y MockMvc), 5 casos:
- cada edición agrega una versión con autor y valores anteriores;
- una oferta sin ediciones tiene solo el alta;
- guardar sin cambios no crea versión;
- una edición rechazada fuera de término no crea versión;
- permisos: ONG dueña, otra ONG, coordinador, municipio y lote equivocado.

```bash
mvn -f rescuesync-backend/pom.xml -Dtest=VersionesOfertaIntegrationTests test
```

## Informe

Con esto, lo que dice el informe sobre las versiones de ofertas (tabla de la sección 4, sección 4.1, CU-03 y sección 7.1) pasa a ser cierto. Conviene agregar `GET /lotes/{id}/ofertas/{id}/versiones` a la tabla de la sección 6 y la entidad `REVISION_OFERTA` al modelo de datos.
