---
name: nueva-funcionalidad
description: Pasos para añadir una funcionalidad completa en Cuquora (migración Flyway, entidad, servicio, endpoint de trabajador o cliente, pantalla, tests de aislamiento y documentación) sin olvidar nada. Úsala cuando haya que guardar datos nuevos o crear un endpoint o pantalla nueva.
---

# Nueva funcionalidad en Cuquora

Orden recomendado. Ejemplo completo y reciente: el resumen del tratamiento (`V8__resumen_tratamiento.sql`, `TreatmentSummaryService`, `POST /worker/appointments/{id}/summary`, `fragments/treatment-summary.html`, `treatment-summary.js`, `TreatmentSummaryTests`).

1. **Entiende antes de escribir.** Busca con `grep` lo que ya existe (servicio, DTO, fragmento, clase CSS). Lee solo los trozos necesarios.
2. **Migración** `V<n>__descripcion_en_espanol.sql` (la siguiente libre está en `CLAUDE.md`), SQL válido en H2 y PostgreSQL: `BOOLEAN DEFAULT FALSE NOT NULL`, `TIMESTAMP(6)`, claves ajenas con nombre `fk_tabla_campo`, índices `ix_tabla_campos`. Nunca edites una migración ya aplicada. Actualiza la siguiente libre en `CLAUDE.md`.
3. **Entidad**: campos con comentario de una línea sobre su porqué. Si la regla depende de la hora, recibe `now` (`CenterClock.now(clock, center)`), no uses `LocalDateTime.now()`.
4. **Servicio** (`@Transactional`): toda la validación aquí, con `BusinessRuleException` en español (dice qué hacer) o `ResourceNotFoundException`. Busca siempre con `findByIdAndCenterId` / `findByIdAndClientId`. Si es un cambio de cita que afecta a la agenda, va en `BookingService` y publica `AppointmentChangedEvent`; si no (notas, resúmenes), servicio propio.
5. **Repositorio**: `@EntityGraph` con todo lo que pinte la vista (sin N+1; `open-in-view=false`).
6. **Controlador** fino (si el endpoint de `/worker` cambia algo del centro que solo debe tocar el dueño, `@CenterOwnerOnly` y un caso en `CenterOwnershipTests`): `@AuthenticationPrincipal AppUserDetails`, `centerId` de la sesión. JSON en controladores `@ApiEndpoint` (errores 404/409 en JSON). Totales y filtros para la vista se calculan aquí, no con proyecciones en Thymeleaf.
7. **Interfaz**: aplica el skill `interfaz-cuquora`. Reutiliza fragmentos y clases; si dos pantallas usan lo mismo, fragmento + JS compartido. Textos del servidor en `innerHTML` siempre con `Cuquora.escapeHtml`.
8. **Tests** (`src/test/java/...`, `@SpringBootTest @AutoConfigureMockMvc @Transactional`, usuarios de `TestUsers`):
   - el caso feliz y cada `BusinessRuleException`;
   - un caso en `CenterIsolationTests` (otro centro → 404 y nada cambia);
   - si hay pantalla nueva, que renderice (`PageRenderingTests`).
   - `JAVA_HOME=/home/alejandro/.jdks/ms-25.0.4.1 mvn -q -o test`, y luego mira `target/surefire-reports/*.txt` (con `-q` no se imprime el resumen).
9. **Comprueba en el navegador** con el skill `capturas`.
10. **Documenta**: `CLAUDE.md` (modelo de datos, estructura, nº de tests), `.claude/docs/pantallas.md` si cambia una pantalla, `PENDIENTES.md` (quita lo cerrado, añade lo que quede) y, si afecta a usuarios, `documentación/`.
11. **Informe final** a Alex: archivos tocados y por qué, tests (número y resultado), capturas antes/después. Sin commit salvo que lo pida.
