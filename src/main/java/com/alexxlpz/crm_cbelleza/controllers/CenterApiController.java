package com.alexxlpz.crm_cbelleza.controllers;

import com.alexxlpz.crm_cbelleza.booking.OpeningHoursPolicy;
import com.alexxlpz.crm_cbelleza.dto.CenterSearchCriteria;
import com.alexxlpz.crm_cbelleza.dto.CenterSummaryDTO;
import com.alexxlpz.crm_cbelleza.exceptions.ApiEndpoint;
import com.alexxlpz.crm_cbelleza.services.AppointmentService;
import com.alexxlpz.crm_cbelleza.services.CenterSearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Map;

/** API pública de centros: buscador/mapa y disponibilidad para el selector de hora. */
@RestController
@ApiEndpoint
@RequestMapping("/api/centers")
public class CenterApiController {

    private final CenterSearchService searchService;
    private final AppointmentService appointmentService;
    private final OpeningHoursPolicy openingHours;

    public CenterApiController(CenterSearchService searchService,
                               AppointmentService appointmentService,
                               OpeningHoursPolicy openingHours) {
        this.searchService = searchService;
        this.appointmentService = appointmentService;
        this.openingHours = openingHours;
    }

    @GetMapping
    public List<CenterSummaryDTO> search(@ModelAttribute CenterSearchCriteria criteria) {
        return searchService.search(criteria);
    }

    @GetMapping("/{id}/availability")
    public Map<String, Object> availability(@PathVariable Long id) {
        return Map.of(
                "slotMinutes", openingHours.slotMinutes(),
                "workingDays", openingHours.workingDays().stream().map(DayOfWeek::getValue).sorted().toList(),
                "openHour", openingHours.openHour(),
                "closeHour", openingHours.closeHour(),
                "closedDates", List.of(),
                "occupied", appointmentService.getOccupiedSlots(id));
    }
}
