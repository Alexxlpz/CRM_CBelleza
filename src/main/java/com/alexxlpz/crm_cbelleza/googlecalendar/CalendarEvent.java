package com.alexxlpz.crm_cbelleza.googlecalendar;

import java.time.LocalDateTime;

/** Datos de una cita tal y como se escriben en Google Calendar. */
public record CalendarEvent(String summary,
                            String description,
                            String location,
                            LocalDateTime start,
                            LocalDateTime end,
                            String timeZone) {
}
