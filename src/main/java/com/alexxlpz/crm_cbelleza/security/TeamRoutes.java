package com.alexxlpz.crm_cbelleza.security;

import java.util.Set;

/**
 * Lo que puede hacer todo el equipo en {@code /worker/**}. Todo lo que no esté aquí es solo del dueño del centro:
 * {@link CenterOwnerInterceptor} lo frena para el resto. Así un endpoint nuevo nace cerrado y, si se olvida apuntarlo,
 * lo nota el equipo (le sale el aviso de «solo el dueño»), no queda abierto a quien vaya probando rutas.
 *
 * <p>Cada entrada es el método HTTP y el patrón exacto del {@code @GetMapping}/{@code @PostMapping} (con el prefijo de
 * clase si lo hay), no la URL: se compara con el patrón que eligió Spring para la petición, así que no hay comodines
 * ni variantes de URL que puedan colarse. {@code TeamRoutesTests} comprueba que cada entrada existe y que todo lo
 * demás responde «solo el dueño» a una trabajadora estándar.
 */
public final class TeamRoutes {

    private TeamRoutes() {
    }

    public static final Set<String> FOR_WHOLE_TEAM = Set.of(
            // Panel, agenda y citas
            "GET /worker/dashboard",
            "GET /worker/calendar",
            "GET /worker/calendar/data",
            "POST /worker/appointments",
            "POST /worker/appointments/{id}/approve",
            "POST /worker/appointments/{id}/reject",
            "POST /worker/appointments/{id}/propose",
            "POST /worker/appointments/{id}/withdraw-proposal",
            "POST /worker/appointments/{id}/reschedule",
            "POST /worker/appointments/{id}/cancel",
            "POST /worker/appointments/{id}/no-show",
            "POST /worker/appointments/{id}/summary",
            "POST /worker/appointments/{id}/media",
            "POST /worker/appointments/{id}/media/{mediaId}/delete",
            "GET /worker/media/{mediaId}",
            // Bloqueos: los de otra persona y cerrar el centro los vuelve a comprobar AgendaBlockService
            "POST /worker/agenda-blocks",
            "POST /worker/agenda-blocks/{id}/delete",

            // Clientes, fichas, ventas y canjes
            "GET /worker/clients",
            "GET /worker/clients/search",
            "GET /worker/clients/{clientId}",
            "POST /worker/clients/create",
            "POST /worker/clients/{clientId}/contact",
            "POST /worker/clients/{clientId}/card",
            "POST /worker/clients/{clientId}/sales",
            "POST /worker/clients/{clientId}/sales/{saleId}/cancel",
            "POST /worker/clients/{clientId}/loyalty/redeem",

            // Carta, inventario y reseñas (ver; cambiar la carta y los productos es del dueño)
            "GET /worker/treatments",
            "GET /worker/inventory",
            "POST /worker/inventory/sales",
            "POST /worker/inventory/{id}/adjust-stock",
            "GET /worker/products/{id}/photo",
            "GET /worker/reviews",

            // «Mi Centro»: ver los apartados, el cartel y su propia cuenta
            "GET /worker/center",
            "GET /worker/center/{section:centro|reservas|equipo|cuenta}",
            "GET /worker/center/cartel",
            "GET /worker/center/qr.svg",
            "GET /worker/center/qr.png",
            "POST /worker/center/cuenta",
            "POST /worker/center/my-photo",
            "POST /worker/center/my-photo/delete",

            // Su propio Google Calendar
            "POST /worker/google-calendar/connect",
            "GET /worker/google-calendar/callback",
            "POST /worker/google-calendar/disconnect");

    /** ¿Puede hacerlo cualquiera del equipo? HEAD cuenta como GET. */
    public static boolean allows(String httpMethod, String pattern) {
        String method = "HEAD".equals(httpMethod) ? "GET" : httpMethod;
        return FOR_WHOLE_TEAM.contains(method + " " + pattern);
    }
}
