package com.alexxlpz.crm_cbelleza.controllers;

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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** API pública de centros: buscador/mapa y disponibilidad para el selector de hora. */
@RestController
@ApiEndpoint
@RequestMapping("/api/centers")
public class CenterApiController {

    private final CenterSearchService searchService;
    private final AgendaAvailabilityService availability;

    public CenterApiController(CenterSearchService searchService,
                               AgendaAvailabilityService availability) {
        this.searchService = searchService;
        this.availability = availability;
    }

    @GetMapping
    public List<CenterSummaryDTO> search(@ModelAttribute CenterSearchCriteria criteria) {
        return searchService.search(criteria);
    }

    /**
     * Horario del centro (tramos de cada día, ver {@code OpeningHours.toJson}) y huecos llenos (sin cabina o sin
     * profesional libre) para el selector de hora del cliente.
     */
    @GetMapping("/{id}/availability")
    public Map<String, Object> availability(@PathVariable Long id) {
        var free = availability.availability(id);
        Map<String, Object> json = new LinkedHashMap<>(free.hours().toJson());
        json.put("closedDates", free.closedDates());
        json.put("occupied", free.occupied());
        return json;
    }
}
