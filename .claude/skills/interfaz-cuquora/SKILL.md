---
name: interfaz-cuquora
description: Reglas de interfaz de Cuquora para que la web parezca hecha a mano y no generada por IA - movimiento y animaciones, componentes reutilizables, estilo visual y textos en español. Úsala SIEMPRE antes de tocar CSS, plantillas Thymeleaf, JS de interfaz o cualquier texto que vea el usuario, y repasa su lista final antes de dar por terminado un cambio visual.
---

# Interfaz de Cuquora: que parezca hecha a mano

Cuquora es una herramienta de trabajo para centros de belleza. Se usa con prisa, entre clienta y clienta, a menudo en el móvil.
El objetivo: **tranquila, rápida y concreta**. Cada adorno tiene que justificar su sitio; si dudas, quítalo.

Las reglas generales (tokens, iconos Lucide, modales, anchos) están en `CLAUDE.md`. Las de cada pantalla, en `.claude/docs/pantallas.md`.

## 1. Movimiento

El movimiento sirve para explicar qué ha pasado (algo se abre, se mueve o se guarda), nunca para decorar.

- **Solo se anima lo que aparece por una acción del usuario**: modal, hoja inferior, menú, desplegable, aviso. El contenido de la página **no entra animado** al cargar (nada de `slideInUp` en tarjetas, paneles, héroes o el formulario de login): una herramienta que se abre 40 veces al día no puede hacer esperar.
- **Duraciones en tokens** (`tokens.css`): `--dur-fast` 120 ms (hover y pulsación), `--dur-base` 180 ms (menús, desplegables, modales), `--dur-slow` 240 ms (hojas y paneles que entran desde un borde). **Cerrar es más rápido que abrir** (≈ 160 ms con `--ease-in`); ejemplo: `.mobile-nav-drawer` y `.mobile-nav-drawer.is-open`.
- **Curvas**: al entrar, `--ease-out`; al salir, `--ease-in`. Nada de rebotes ni `cubic-bezier` con sobreimpulso.
- **Solo colores, `opacity` y `transform`.** Para hover usa `transition: var(--transition)` (ya lista solo esas propiedades a 120 ms). Nunca `transition: all`: anima anchos, rellenos y alturas que no querías y la página «baila» al cambiar de tema o de tamaño.
- Scroll desde JS con `behavior: Cuquora.scrollBehavior()` (respeta «reducir movimiento»), nunca `'smooth'` a pelo.
- **Distancias pequeñas**: un modal entra con `scale(.98)` → `1` y opacidad, no con 24 px de desplazamiento; un desplegable baja 4 px como mucho.
- **Hover sin «levitar»**: las tarjetas no suben (`translateY(-4px)`) ni ganan sombra de color. Basta con cambiar el borde o el fondo. Los botones, como mucho, se oscurecen; al pulsar, `:active { transform: scale(.98) }`.
- **Nada infinito** salvo el indicador de carga (`spin`): ni brillos, ni latidos (`gentlePulse`, `pulseGlow`), ni insignias flotando.
- **Sin cascadas** (cada tarjeta 50 ms después de la anterior). Los listados aparecen de golpe.
- Respeta `prefers-reduced-motion` (ya hay un bloque en `common.css`): cualquier animación nueva tiene que quedar desactivada ahí.
- Cuando algo se guarda, lo normal es **cambiar el propio elemento** (el texto del botón, el bloque que se actualiza) en vez de lanzar un aviso flotante.

## 2. Estilo visual: lo que delata a la IA

Evita estos rasgos (todos están hoy en el código; ver «Calidad visual» en `PENDIENTES.md`):

| Delata | Haz en su lugar |
|---|---|
| Degradados en botones, insignias, textos y separadores | Color plano de `tokens.css`. Un degradado solo si imita algo real (una foto, un producto) |
| `backdrop-filter: blur()` en tarjetas sobre fondo liso | Fondo `--surface` opaco. El desenfoque solo tiene sentido sobre una imagen o contenido que pasa por debajo (barra fija, modal) |
| Sombras de color (`rgba(var(--primary-rgb), .2)`) y sombras grandes en todo | Borde de 1 px (`--border-soft`). Sombra solo en lo que flota: modales, menús, hojas |
| Emojis como iconos (📅 📦 🚨 en `features.html`) | Icono Lucide o nada |
| ✨ `lucide-sparkles` para «nuevo» o «profesional» | Texto claro («Primera visita») o un icono con significado |
| Etiquetas en MAYÚSCULAS con `letter-spacing` en cada tarjeta | Minúscula normal y peso 600. Mayúsculas solo en alguna cabecera de tabla |
| Todo redondeado a píldora, todo centrado, todo con icono | Radios de los tokens (`--border-radius-sm/md`), texto alineado a la izquierda, iconos donde ayudan a reconocer algo |
| Tres columnas de tarjetas iguales con icono + título + frase | Contenido real del producto: capturas de pantallas, un ejemplo de agenda, datos concretos |

## 3. Componentes: reutiliza antes de crear

- **Antes de escribir una clase nueva, busca una que ya haga lo mismo** (`grep -n "^\.btn" src/main/resources/static/css/common.css`). Hoy hay más de 30 variantes de botón (`btn-save-ficha`, `btn-template-config`, `btn-add-treatment`…) que hacen lo que ya hace `.btn` + modificador.
- Botones: `.btn` + `.btn-primary` (una sola acción principal por bloque) / `.btn-secondary` / `.btn-danger`, y tamaño `.btn-sm` o `.btn-lg`. Para separar o colocar, una clase de layout aparte (p. ej. `.summary-button { margin-top }`), no una familia de botón nueva.
- Formularios en modal: `.ui-modal-overlay` > `.ui-modal.ui-modal--form`, `.form-label`, `.form-control`, `.form-hint`, `.checkbox-row`, `.agenda-form-error`, `.ui-modal-actions`. Ejemplo completo y pequeño: `templates/fragments/treatment-summary.html`.
- Estados de cita: `.badge.badge-<estado en minúscula>` con `AppointmentStatus.label()`; no repitas un `th:if` por estado.
- Un fragmento Thymeleaf + un JS pequeño por componente compartido (como `treatment-summary`), no copias del mismo marcado en dos plantillas.
- **Componente innecesario** = no aporta información ni acción: contadores de caracteres, insignias decorativas, tooltips que repiten el texto, iconos en cada línea de una lista, separadores con degradado, «chips» que no se pueden pulsar. Si al quitarlo no se pierde nada, quítalo.

## 4. Textos

- **Mayúscula solo al principio** (estilo español, no inglés): «Guardar ficha del cliente», «Configurar plantilla», «Nueva cita». Solo llevan mayúscula los nombres propios (Cuquora, Google Calendar).
- **Concretos y cortos**. El botón dice lo que hace: «Guardar resumen», no «Enviar» ni «Aceptar».
- Sin muletillas de máquina: nada de «con éxito», «de forma fluida», «100 %», «potencia», «experiencia única», «todo en uno», «sin esfuerzo». «Ficha guardada.» basta.
- Los errores dicen qué hacer: «Escribe el resumen para poder marcarlo como aviso.», no «Error de validación».
- Ejemplos y marcadores de posición, realistas y del sector (productos, tratamientos y reacciones de verdad).
- Sin exclamaciones ni emojis en la interfaz ni en los correos.

## 5. Código que se lee como escrito por una persona

- Comentarios en español y solo para el **porqué** («Las propuestas reservan hueco frente a clientes»), no para describir el CSS («Solid crisp white», «Hero Section»).
- Borra lo que no se usa: `@keyframes` sin uso, clases huérfanas, comentarios de secciones vacías.
- Nada de estilos inline en plantillas ni JS que ponga `style=` salvo variables CSS calculadas (`--top`, `--height`) o alturas medidas.

## 6. Antes de dar por terminado un cambio visual

1. ¿Has reutilizado clases existentes? ¿Hay alguna clase nueva que se pueda quitar?
2. ¿Alguna animación de carga, infinita, de más de 240 ms o con `transition: all`?
3. ¿Degradados, desenfoques, sombras de color, emojis, mayúsculas espaciadas o `sparkles` nuevos?
4. ¿Los textos van en minúscula tras la primera palabra, sin «con éxito» ni exclamaciones?
5. ¿Modo oscuro correcto (colores solo con tokens)?
6. Capturas con el skill **`capturas`** a 1920, 1440, 1100, 960, 768 y 390 px, antes y después: nada cortado, nada fuera de su sitio.
