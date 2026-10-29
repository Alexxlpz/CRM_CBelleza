---
name: capturas
description: Arranca Cuquora en local y saca capturas de cualquier pantalla (con sesión de trabajador o cliente) a 1920, 1440, 1100, 960, 768 y 390 px, antes y después de un cambio, sin instalar nada. Úsala para comprobar cualquier cambio visual antes de darlo por terminado.
---

# Capturas de pantalla de Cuquora

Todo sale de aquí sin dependencias: Node 22+ y Brave/Chrome sin ventana (`shot.mjs`, protocolo DevTools).
Guarda las capturas en el directorio temporal de la sesión (scratchpad), nunca en el repositorio.

## 1. Arrancar la app (después del cambio)

El 8080 suele estar ocupado por la app de Alex en IntelliJ: **no la pares**, usa el 8091.

```bash
JAVA_HOME=/home/alejandro/.jdks/ms-25.0.4.1 mvn -q -o spring-boot:run \
  -Dspring-boot.run.profiles=dev -Dspring-boot.run.arguments=--server.port=8091   # en segundo plano
curl -s -o /dev/null -w "%{http_code}\n" localhost:8091/login                      # 200 = lista (~20 s)
```

Tras editar CSS/JS/plantillas con la app ya arrancada: `mvn -q -o resources:resources` (no hace falta reiniciar).

## 2. Versión de antes (para comparar)

```bash
git worktree add -q "$SCRATCH/antes" HEAD
(cd "$SCRATCH/antes" && JAVA_HOME=/home/alejandro/.jdks/ms-25.0.4.1 nohup mvn -q -o spring-boot:run \
  -Dspring-boot.run.profiles=dev -Dspring-boot.run.arguments=--server.port=8092 > "$SCRATCH/antes.log" 2>&1 &)
```

## 3. Capturar

```bash
S=.claude/skills/capturas/shot.mjs
node $S --base http://localhost:8091 --user carlos@cbelleza.com --path /worker/clients/6 --name despues-ficha --out "$SCRATCH/capturas"
node $S --base http://localhost:8092 --user carlos@cbelleza.com --path /worker/clients/6 --name antes-ficha --widths 1440,390 --out "$SCRATCH/capturas"
# Con un modal abierto (captura solo lo visible):
node $S --user carlos@cbelleza.com --path "/worker/calendar?date=2026-10-12&view=month" --name modal --widths 1100,390 \
  --run "document.querySelector('[data-action=summary]')?.click()"
```

- Usuarios demo (contraseña `Cuquora2026!`, la que usa el script por defecto): trabajadores `carlos@cbelleza.com`, `elena@cbelleza.com`; cliente `sofia@gmail.com`. Sin `--user`, páginas públicas. Ojo: 5 logins fallidos con una cuenta (o 20 desde la IP) la cierran 15 min; reiniciar la app lo borra.
- Con cliente: el aviso de cookies vuelve a salir tras el login y puede salir la tarjeta «¿Qué tal tu cita?»; pulsa «Entendido» (y cierra la tarjeta si estorba) con `--run` antes de capturar.
- En el buscador (`/centers`) usa `--wait 9000`: espera a la geolocalización.
- **En Windows (Git Bash)** exporta antes `MSYS_NO_PATHCONV=1`: si no, `--path /worker/...` se convierte en una ruta de Git y la captura sale en blanco (el script avisa con «Navegación fallida»). El script busca Brave o Chrome también en `C:/Program Files`.
- Ids útiles con datos demo: Sofía Martínez es el cliente 6 del centro de Carlos.
- Mira las capturas con la herramienta de leer imágenes. Empieza por 1440 y 390; abre las demás solo si el cambio afecta al diseño en esos anchos (960-1100 es donde más se rompe).
- La primera carga de una pantalla con mucho JS (la agenda) puede tardar más de 2 s: si la captura sale con el texto de ejemplo de la plantilla («Julio 2026») o sin la tipografía, repite con `--wait 4000`.
- Para comparar antes/después sin mirar todas las imágenes: `magick compare -metric AE -fuzz 3% antes.png despues.png null:` (0 = idénticas) y abre solo las que cambian.
- Los datos de prueba que crees en la app arrancada (H2 en memoria) desaparecen al pararla.

## 4. Recoger

Para **solo** los procesos que arrancaste (8091/8092), nunca el 8080:

```bash
for p in 8091 8092; do pid=$(ss -ltnpH "sport = :$p" | grep -oP 'pid=\K[0-9]+' | head -1); [ -n "$pid" ] && kill $pid; done
git worktree remove --force "$SCRATCH/antes"; git worktree prune
```
