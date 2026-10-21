package com.alexxlpz.crm_cbelleza.booking;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Set;

/**
 * Reservas configurables desde application.properties (prefijo {@code crm.booking}): la franja de las citas
 * y el horario que se propone al dar de alta un centro (cada centro tiene después el suyo, ver
 * {@link OpeningHours}). Por defecto, franjas de 30 minutos y lunes a viernes de 9:00 a 20:00.
 */
@ConfigurationProperties(prefix = "crm.booking")
public class BookingProperties {

    private int slotMinutes = 30;
    private int openHour = 9;
    private int closeHour = 20;
    private Set<DayOfWeek> workingDays = EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);

    public int slotMinutes() { return slotMinutes; }

    /** Horario que se propone a un centro nuevo y el de un centro que todavía no tiene ninguno. */
    public OpeningHours defaultHours() {
        return OpeningHours.uniform(slotMinutes, workingDays, LocalTime.of(openHour, 0), LocalTime.of(closeHour, 0));
    }

    public void setSlotMinutes(int slotMinutes) { this.slotMinutes = slotMinutes; }
    public void setOpenHour(int openHour) { this.openHour = openHour; }
    public void setCloseHour(int closeHour) { this.closeHour = closeHour; }
    public void setWorkingDays(Set<DayOfWeek> workingDays) { this.workingDays = workingDays; }
}
