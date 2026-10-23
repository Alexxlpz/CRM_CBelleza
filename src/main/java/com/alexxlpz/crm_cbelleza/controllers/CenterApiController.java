package com.alexxlpz.crm_cbelleza.controllers;

import com.alexxlpz.crm_cbelleza.dto.CenterSearchCriteria;
import com.alexxlpz.crm_cbelleza.dto.CenterSummaryDTO;
import com.alexxlpz.crm_cbelleza.exceptions.ApiEndpoint;
import com.alexxlpz.crm_cbelleza.security.AppUserDetails;
import com.alexxlpz.crm_cbelleza.services.AgendaAvailabilityService;
import com.alexxlpz.crm_cbelleza.services.CenterSearchService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
     * profesional libre) para el selector de hora del cliente. Con {@code treatmentId} y {@code workerId}, los huecos
     * en los que no queda libre ese profesional o nadie que haga el tratamiento; con {@code changing}, la cita del
     * cliente que se está cambiando de hora no ocupa su hueco.
     */
    @GetMapping("/{id}/availability")
    public Map<String, Object> availability(@PathVariable Long id,
                                            @RequestParam(required = false) Long treatmentId,
                                            @RequestParam(required = false) Long workerId,
                                            @RequestParam(required = false) Long changing,
                                            @AuthenticationPrincipal AppUserDetails user) {
        var free = availability.availability(id, treatmentId, workerId, changing, user != null ? user.getId() : null);
        Map<String, Object> json = new LinkedHashMap<>(free.hours().toJson());
        json.put("closedDates", free.closedDates());
        json.put("occupied", free.occupied());
        return json;
    }
}
