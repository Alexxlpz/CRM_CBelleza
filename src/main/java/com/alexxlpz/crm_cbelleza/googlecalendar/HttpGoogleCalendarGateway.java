package com.alexxlpz.crm_cbelleza.googlecalendar;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/** {@link GoogleCalendarGateway} con llamadas HTTP directas a Google (sin librerías de Google). */
@Component
public class HttpGoogleCalendarGateway implements GoogleCalendarGateway {

    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String REVOKE_URL = "https://oauth2.googleapis.com/revoke";
    private static final String CALENDAR_API = "https://www.googleapis.com/calendar/v3";
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final GoogleCalendarSettings settings;
    private final ObjectMapper json;
    private final RestClient http = RestClient.create();

    public HttpGoogleCalendarGateway(GoogleCalendarSettings settings, ObjectMapper json) {
        this.settings = settings;
        this.json = json;
    }

    @Override
    public GoogleAuthorization exchangeCode(String code, String redirectUri) {
        MultiValueMap<String, String> form = clientForm();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", redirectUri);
        JsonNode response = postForm(TOKEN_URL, form, "canjear el código de Google");
        return new GoogleAuthorization(text(response, "refresh_token"), emailFromIdToken(text(response, "id_token")));
    }

    @Override
    public String createCalendar(String refreshToken, String name, String timeZone) {
        Map<String, Object> body = Map.of("summary", name, "timeZone", timeZone);
        return text(send(HttpMethod.POST, CALENDAR_API + "/calendars", refreshToken, body), "id");
    }

    @Override
    public void deleteCalendar(String refreshToken, String calendarId) {
        sendIgnoringMissing(HttpMethod.DELETE, CALENDAR_API + "/calendars/{calendar}", refreshToken, calendarId);
    }

    @Override
    public String insertEvent(String refreshToken, String calendarId, CalendarEvent event) {
        return text(send(HttpMethod.POST, CALENDAR_API + "/calendars/{calendar}/events", refreshToken, eventBody(event), calendarId), "id");
    }

    @Override
    public boolean updateEvent(String refreshToken, String calendarId, String eventId, CalendarEvent event) {
        try {
            send(HttpMethod.PUT, CALENDAR_API + "/calendars/{calendar}/events/{event}", refreshToken, eventBody(event), calendarId, eventId);
            return true;
        } catch (GoogleCalendarException e) {
            if (e.getCause() instanceof RestClientResponseException response && isMissing(response)) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public void deleteEvent(String refreshToken, String calendarId, String eventId) {
        sendIgnoringMissing(HttpMethod.DELETE, CALENDAR_API + "/calendars/{calendar}/events/{event}", refreshToken, calendarId, eventId);
    }

    @Override
    public void revoke(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("token", refreshToken);
        postForm(REVOKE_URL, form, "retirar el permiso de Google");
    }

    private String accessToken(String refreshToken) {
        MultiValueMap<String, String> form = clientForm();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);
        return text(postForm(TOKEN_URL, form, "renovar el acceso a Google"), "access_token");
    }

    private JsonNode send(HttpMethod method, String url, String refreshToken, Object body, Object... uriVariables) {
        String accessToken = accessToken(refreshToken);
        try {
            var request = http.method(method)
                    .uri(url, uriVariables)
                    .headers(headers -> headers.setBearerAuth(accessToken));
            if (body != null) {
                request = request.contentType(MediaType.APPLICATION_JSON).body(json.writeValueAsString(body));
            }
            return parse(request.retrieve().body(String.class));
        } catch (JsonProcessingException | RestClientException e) {
            throw new GoogleCalendarException("Error de Google Calendar (" + method + " " + url + ")", false, e);
        }
    }

    private void sendIgnoringMissing(HttpMethod method, String url, String refreshToken, Object... uriVariables) {
        try {
            send(method, url, refreshToken, null, uriVariables);
        } catch (GoogleCalendarException e) {
            if (!(e.getCause() instanceof RestClientResponseException response && isMissing(response))) {
                throw e;
            }
        }
    }

    private JsonNode postForm(String url, MultiValueMap<String, String> form, String action) {
        try {
            return parse(http.post().uri(url)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(String.class));
        } catch (RestClientResponseException e) {
            // invalid_grant: el trabajador quitó el permiso desde su cuenta de Google o el token caducó
            boolean revoked = e.getStatusCode().value() == 400 && e.getResponseBodyAsString().contains("invalid_grant");
            throw new GoogleCalendarException("No se ha podido " + action + ".", revoked, e);
        } catch (RestClientException | JsonProcessingException e) {
            throw new GoogleCalendarException("No se ha podido " + action + ".", false, e);
        }
    }

    private MultiValueMap<String, String> clientForm() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", settings.clientId());
        form.add("client_secret", settings.clientSecret());
        return form;
    }

    private static Map<String, Object> eventBody(CalendarEvent event) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("summary", event.summary());
        body.put("description", event.description());
        body.put("location", event.location());
        body.put("start", when(event.start(), event.timeZone()));
        body.put("end", when(event.end(), event.timeZone()));
        return body;
    }

    private static Map<String, String> when(LocalDateTime dateTime, String timeZone) {
        return Map.of("dateTime", DATE_TIME.format(dateTime), "timeZone", timeZone);
    }

    private static boolean isMissing(RestClientResponseException response) {
        int status = response.getStatusCode().value();
        return status == HttpStatus.NOT_FOUND.value() || status == HttpStatus.GONE.value();
    }

    private JsonNode parse(String body) throws JsonProcessingException {
        return body == null || body.isBlank() ? json.createObjectNode() : json.readTree(body);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    /**
     * Correo de la cuenta, leído del id_token. No hace falta comprobar la firma: llega directamente
     * de Google por HTTPS en la respuesta del canje del código.
     */
    private String emailFromIdToken(String idToken) {
        if (idToken == null || idToken.split("\\.").length < 2) {
            return null;
        }
        try {
            byte[] payload = Base64.getUrlDecoder().decode(idToken.split("\\.")[1]);
            return text(json.readTree(new String(payload, StandardCharsets.UTF_8)), "email");
        } catch (IllegalArgumentException | JsonProcessingException e) {
            return null;
        }
    }
}
