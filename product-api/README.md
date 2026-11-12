# API de productos de Cuquora

Base de datos propia de productos de belleza por código de barras. Corre en su propio contenedor (`product-api` en
el `docker-compose.yml` de la raíz) y solo la usa la app de Cuquora, por la red interna de Docker y con clave.

- Si un código no está aquí, lo busca en Open Beauty Facts y lo guarda (datos y foto) para no volver a preguntar.
- Cuando un centro da de alta en Cuquora un producto con código de barras, Cuquora le manda el nombre, la categoría,
  la descripción y la foto. Nunca el precio ni el stock. Lo que llega solo rellena huecos: no cambia el
  nombre ni una foto que ya hay.
- Guarda los datos en la misma PostgreSQL que Cuquora, en su propio esquema `productos` (entra en la copia diaria),
  con su propio usuario `productos_api`: es dueño de ese esquema y no tiene ningún permiso sobre las tablas de
  Cuquora. Lo prepara el servicio `db-setup` del `docker-compose.yml` en cada arranque, también tras restaurar una copia.
- Las fotos se guardan reducidas a 800 px por el lado largo, en JPEG (lo transparente de un PNG queda en blanco).
  Las que ya son pequeñas (hasta 800 px y 300 KB) se guardan tal cual. Se rechazan las de más de 16 megapíxeles.

## Endpoints

Todos los de `/api` piden la cabecera `X-Api-Key` con `PRODUCT_API_KEY`.

| Método | Ruta | Qué hace |
|---|---|---|
| GET | `/api/products/{código}` | Datos del producto (`gtin`, `name`, `brand`, `category`, `quantity`, `description`, `source`, `image`) o 404 |
| GET | `/api/products/{código}/image` | Foto del envase (JPEG o PNG) |
| POST | `/api/products/{código}` | Un centro aporta el producto (multipart: `name`, `brand`, `category`, `quantity`, `description`, `image`, `center`). 201 si es nuevo, 200 si ya estaba. `center` es el número del centro en Cuquora y queda en el registro de aportaciones |
| GET | `/health` | Para el healthcheck de Docker (sin clave): 200 `UP` si la base de datos responde, 503 `DOWN` si no |

## Configuración

| Variable | Para qué |
|---|---|
| `PRODUCT_API_KEY` | Clave compartida con Cuquora, de al menos 32 caracteres (`openssl rand -hex 32`). Sin ella no arranca |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL. Sin `DB_URL` usa una base H2 en memoria (para probar). En Docker entra como `productos_api` con `PRODUCT_API_DB_PASSWORD` del `.env` |
| `OPEN_BEAUTY_FACTS_ENABLED` | `false` para no consultar Open Beauty Facts (solo lo que aportan los centros) |

Para probarla en local: `mvn spring-boot:run` con `PRODUCT_API_KEY` puesta; escucha en el puerto 8081.

## Corregir o quitar un producto

Lo que aporta un centro no se puede cambiar desde Cuquora (es a propósito: así nadie estropea lo que ven los demás).
Para arreglarlo está `docker/corregir-producto.sh`, que se lanza en el servidor junto al `docker-compose.yml`:

```sh
sh docker/corregir-producto.sh 8436595340863 ver                          # datos, foto y quién lo aportó
sh docker/corregir-producto.sh 8436595340863 nombre "Alissi Brontë Sublime"
sh docker/corregir-producto.sh 8436595340863 categoria Cabello            # Rostro, Cabello, Uñas o Cuerpo
sh docker/corregir-producto.sh 8436595340863 descripcion ""               # "" lo deja vacío (también marca y formato)
sh docker/corregir-producto.sh 8436595340863 quitar-foto
sh docker/corregir-producto.sh 8436595340863 borrar                       # pide confirmación
```

Cada corrección queda en el registro como `corregido:<campo>`, sin centro. Solo cambia la API: los productos que los
centros ya tienen en su inventario son suyos y no se tocan. Un producto borrado se vuelve a crear la próxima vez que
alguien lo dé de alta. En tu PC, con el Docker local: `DB_CONTAINER=cuquora_local_db sh docker/corregir-producto.sh ...`
(en Windows, desde Git Bash).

## Saber qué centro aportó cada producto

Cada vez que un centro rellena algo (da de alta el producto, o pone una foto, categoría o descripción que faltaba), queda
una fila en `productos.product_contributions`: el código, el número del centro en Cuquora (`center_id`), si lo dio de
alta él (`created`), qué datos rellenó (`fields`, por ejemplo `nombre,categoria,foto`) y cuándo. Si no rellena nada
nuevo no se apunta. Las aportaciones de antes del 12 nov 2026 no tienen registro.

Para entrar en la base de datos:

```sh
docker exec -it crm_cbelleza_db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```

```sql
-- Quién aportó qué en un producto
SELECT center_id, created, fields, created_at FROM productos.product_contributions
 WHERE gtin = '8436595340863' ORDER BY id;

-- Todo lo que ha aportado el centro 7
SELECT gtin, created, fields, created_at FROM productos.product_contributions
 WHERE center_id = 7 ORDER BY id;

-- Borrar los productos que dio de alta el centro 7 (su registro se borra con ellos)
DELETE FROM productos.products
 WHERE gtin IN (SELECT gtin FROM productos.product_contributions WHERE center_id = 7 AND created);

-- Quitar las fotos que puso el centro 7 en productos de otros
DELETE FROM productos.product_images
 WHERE gtin IN (SELECT gtin FROM productos.product_contributions
                 WHERE center_id = 7 AND NOT created AND fields LIKE '%foto%');
```

Las marcas, categorías o descripciones que rellenó en productos de otros se corrigen a mano con un `UPDATE`.
