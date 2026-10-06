package com.alexxlpz.crm_cbelleza.booking;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.DayOfWeek;
import java.util.EnumSet;
import java.util.Set;

/**
 * Horario configurable desde application.properties (prefijo {@code crm.booking}).
 * Valores por defecto: lunes a viernes de 9:00 a 20:00 en franjas de 30 minutos.
 */
@ConfigurationProperties(prefix = "crm.booking")
public class BookingProperties implements OpeningHoursPolicy {

    private int slotMinutes = 30;
    private int openHour = 9;
    private int closeHour = 20;
    private Set<DayOfWeek> workingDays = EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);

    @Override
    public int slotMinutes() { return slotMinutes; }

    @Override
    public int openHour() { return openHour; }

    @Override
    public int closeHour() { return closeHour; }

    @Override
    public Set<DayOfWeek> workingDays() { return workingDays; }

    public void setSlotMinutes(int slotMinutes) { this.slotMinutes = slotMinutes; }
    public void setOpenHour(int openHour) { this.openHour = openHour; }
    public void setCloseHour(int closeHour) { this.closeHour = closeHour; }
    public void setWorkingDays(Set<DayOfWeek> workingDays) { this.workingDays = workingDays; }
}
