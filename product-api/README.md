# API de productos de Cuquora

Base de datos propia de productos de belleza por código de barras. Corre en su propio contenedor (`product-api` en
el `docker-compose.yml` de la raíz) y solo la usa la app de Cuquora, por la red interna de Docker y con clave.

- Si un código no está aquí, lo busca en Open Beauty Facts y lo guarda (datos y foto) para no volver a preguntar.
- Cuando un centro da de alta en Cuquora un producto con código de barras, Cuquora le manda el nombre, la categoría,
  la descripción y la foto. Nunca el precio ni el stock. Lo que llega solo rellena huecos: no cambia el
  nombre ni una foto que ya hay.
- Guarda los datos en la misma PostgreSQL que Cuquora, en su propio esquema `productos` (entra en la copia diaria).

## Endpoints

Todos los de `/api` piden la cabecera `X-Api-Key` con `PRODUCT_API_KEY`.

| Método | Ruta | Qué hace |
|---|---|---|
| GET | `/api/products/{código}` | Datos del producto (`gtin`, `name`, `brand`, `category`, `quantity`, `description`, `source`, `image`) o 404 |
| GET | `/api/products/{código}/image` | Foto del envase (JPEG o PNG) |
| POST | `/api/products/{código}` | Un centro aporta el producto (multipart: `name`, `brand`, `category`, `quantity`, `description`, `image`). 201 si es nuevo, 200 si ya estaba |
| GET | `/health` | Para el healthcheck de Docker (sin clave) |

## Configuración

| Variable | Para qué |
|---|---|
| `PRODUCT_API_KEY` | Clave compartida con Cuquora, de al menos 32 caracteres (`openssl rand -hex 32`). Sin ella no arranca |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL. Sin `DB_URL` usa una base H2 en memoria (para probar) |
| `OPEN_BEAUTY_FACTS_ENABLED` | `false` para no consultar Open Beauty Facts (solo lo que aportan los centros) |

Para probarla en local: `mvn spring-boot:run` con `PRODUCT_API_KEY` puesta; escucha en el puerto 8081.

## Quitar un producto mal dado de alta

Lo que aporta un centro no se puede cambiar desde Cuquora (es a propósito: así nadie estropea lo que ven los demás).
Si alguno está mal, se borra en la base de datos y se vuelve a crear la próxima vez que alguien lo dé de alta:

```sh
docker exec -it crm_cbelleza_db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "DELETE FROM productos.products WHERE gtin = '"'"'8436595340863'"'"'"'
```
