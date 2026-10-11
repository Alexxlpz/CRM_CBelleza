package com.alexxlpz.crm_cbelleza.controllers;

import com.alexxlpz.crm_cbelleza.booking.OpeningHoursPolicy;
import com.alexxlpz.crm_cbelleza.dto.CenterSearchCriteria;
import com.alexxlpz.crm_cbelleza.dto.CenterSummaryDTO;
import com.alexxlpz.crm_cbelleza.exceptions.ApiEndpoint;
import com.alexxlpz.crm_cbelleza.services.AgendaAvailabilityService;
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
    private final AgendaAvailabilityService availability;
    private final OpeningHoursPolicy openingHours;

    public CenterApiController(CenterSearchService searchService,
                               AgendaAvailabilityService availability,
                               OpeningHoursPolicy openingHours) {
        this.searchService = searchService;
        this.availability = availability;
        this.openingHours = openingHours;
    }

    @GetMapping
    public List<CenterSummaryDTO> search(@ModelAttribute CenterSearchCriteria criteria) {
        return searchService.search(criteria);
    }

    /** Horario y huecos llenos (sin cabina o sin profesional libre) para el selector de hora del cliente. */
    @GetMapping("/{id}/availability")
    public Map<String, Object> availability(@PathVariable Long id) {
        var free = availability.availability(id);
        return Map.of(
                "slotMinutes", openingHours.slotMinutes(),
                "workingDays", openingHours.workingDays().stream().map(DayOfWeek::getValue).sorted().toList(),
                "openHour", openingHours.openHour(),
                "closeHour", openingHours.closeHour(),
                "closedDates", free.closedDates(),
                "occupied", free.occupied());
    }
}
