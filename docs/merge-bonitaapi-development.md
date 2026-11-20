# Merge de `feature/bonitaapi` (E3-10) en `development`

Fecha: 19/11/2026. Resuelve el ajuste 1 de [`e4-01-ajustes-pendientes.md`](e4-01-ajustes-pendientes.md).

## Por qué hubo que unir dos procesos

El `.bos` se había separado en dos versiones a partir del mismo proceso base (`8403859`, RescueSync 1.1 con ofertas consolidadas):

| Rama | Proceso | Qué agregaba |
| --- | --- | --- |
| `development` | 1.1 | E4-02: decisión de cobertura (`f40a143`) |
| `feature/bonitaapi` | 1.2 | E3-10: conectores del Sistema Nacional para evaluar, comprometer y liberar, y selección de una oferta por ciclo (`3cf2123`) |

Git no puede mergear un `.bos`, porque es un zip. Desde la base común, la 1.1 de `development` solo había cambiado E4-02. Sus `pom.xml` diferían únicamente en espacios y el `MANIFEST` solo en la fecha. No se perdió nada más.

## Cómo se armó el `.bos`

1. Se partió del `.bos` de `feature/bonitaapi` (1.2), con sus conectores, JARs y configuración.
2. Sobre su diagrama se aplicaron los 7 cambios de E4-02, como reemplazos exactos de texto calculados entre la base y la 1.1. El XML no se volvió a serializar, así que no aplica el problema de Python anterior a 3.13 (ajuste 22).
   - Variable `decisionCobertura`.
   - Contrato `decisionCoberturaInput` con la restricción `decisionValida`, y la operación que asigna la variable.
   - Las tres condiciones del gateway "¿Lotes cubiertos?". La salida a "Publicar convocatoria" pasó a llamarse `sin cobertura` y quedó como default.
3. Se verificó que el XML es válido, que no hay `xmi:id` repetidos y que el zip abre bien.

**La versión queda en 1.2.** `scripts/configurar_bonita_e3_10.py` la tiene fija, y la doc y el backend se refieren a 1.2. El diagrama 1.2 ahora incluye E4-02: no es el mismo que se desplegó antes desde `feature/bonitaapi`.

## Otros cambios del merge

- `Ong`: conserva `notificaciones` (E4-04) y suma `cuit` (E3-10). Para una base existente, correr `scripts/ongs_cuit.sql` (ver [`e3-10-sistema-nacional.md`](e3-10-sistema-nacional.md)).
- `README.md`: quedan las secciones de E3-10 y E4.
- Se sacó del repo `bonita-sistema-nacional-connector/target/`, que se había commiteado por error en `05f8d2e`. El `.gitignore` del módulo ya lo excluye y el `.bos` trae el JAR.

## Pruebas

| Prueba | Resultado |
| --- | --- |
| Backend, `mvn test` | 148/148 |
| Conector del SN, `mvn test` con JDK 21 | 20/20 |
| Despliegue en Bonita Studio | **Pendiente** |

## Qué hacer para usarlo

1. Importar `rescue-sync.bos` en Studio 2025.2. Si ya había un RescueSync 1.2 desplegado, el nuevo lo reemplaza: los casos abiertos de esa versión se pierden. Probar con emergencias nuevas.
2. Dejar habilitada solo la 1.2. El backend toma la primera versión habilitada de `RescueSync`.
3. Cargar las credenciales técnicas en el entorno de Bonita: las dos de E3-09 y las dos del Sistema Nacional (ver [`e3-10-sistema-nacional.md`](e3-10-sistema-nacional.md)).
4. Probar el camino de cobertura (`docs/e4-02-cobertura.md`, sección 4) y seguir hasta "Evaluar ofertas". Ahí ya corre el conector del SN.
