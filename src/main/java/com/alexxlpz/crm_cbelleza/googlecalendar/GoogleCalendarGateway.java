package com.alexxlpz.crm_cbelleza.googlecalendar;

/**
 * Llamadas a Google (OAuth y Calendar API). Cada método recibe el token de actualización del trabajador
 * y pide un token de acceso nuevo. Los errores se lanzan como {@link GoogleCalendarException}.
 */
public interface GoogleCalendarGateway {

    GoogleAuthorization exchangeCode(String code, String redirectUri);

    /** Crea un calendario propio en la cuenta del trabajador y devuelve su id. */
    String createCalendar(String refreshToken, String name, String timeZone);

    void deleteCalendar(String refreshToken, String calendarId);

    String insertEvent(String refreshToken, String calendarId, CalendarEvent event);

    /** Devuelve {@code false} si el evento ya no existe (p. ej. lo borró el trabajador en Google). */
    boolean updateEvent(String refreshToken, String calendarId, String eventId, CalendarEvent event);

    void deleteEvent(String refreshToken, String calendarId, String eventId);

    /** Retira el permiso concedido a Cuquora en la cuenta de Google. */
    void revoke(String refreshToken);
}
