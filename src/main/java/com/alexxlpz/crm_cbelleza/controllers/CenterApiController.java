package com.alexxlpz.crm_cbelleza.controllers;

import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.entities.Treatment;
import com.alexxlpz.crm_cbelleza.repositories.CenterRepository;
import com.alexxlpz.crm_cbelleza.repositories.TreatmentRepository;
import com.alexxlpz.crm_cbelleza.services.AppointmentService;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/centers")
public class CenterApiController {

    private final CenterRepository centerRepository;
    private final TreatmentRepository treatmentRepository;
    private final AppointmentService appointmentService;

    public CenterApiController(CenterRepository centerRepository, TreatmentRepository treatmentRepository,
                               AppointmentService appointmentService) {
        this.centerRepository = centerRepository;
        this.treatmentRepository = treatmentRepository;
        this.appointmentService = appointmentService;
    }

    @GetMapping("/{id}/availability")
    public Map<String, Object> getAvailability(@PathVariable Long id) {
        List<String> occupied = appointmentService.getBookableAppointmentsByCenter(id).stream()
                .flatMap(appointment -> {
                    int slots = (int) Math.ceil(appointment.getTreatment().getDuration() / 30.0);
                    return java.util.stream.IntStream.range(0, slots)
                            .mapToObj(slot -> appointment.getDateTime().plusMinutes(slot * 30L).toString());
                })
                .toList();
        return Map.of(
                "slotMinutes", 30,
                "workingDays", List.of(1, 2, 3, 4, 5),
                "openHour", 9,
                "closeHour", 20,
                "closedDates", List.of(),
                "occupied", occupied
        );
    }

    public static class CenterDTO {
        public Long id;
        public String name;
        public String address;
        public String phone;
        public String email;
        public Double distanceKm;
        public Double latitude;
        public Double longitude;

        public CenterDTO(Center c, Double distanceKm) {
            this.id = c.getId();
            this.name = c.getName();
            this.address = c.getAddress();
            this.phone = c.getPhone();
            this.email = c.getEmail();
            this.distanceKm = distanceKm;
            this.latitude = c.getLatitude();
            this.longitude = c.getLongitude();
        }
    }

    @GetMapping
    public List<CenterDTO> getCenters(
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lon,
            @RequestParam(required = false) Double north,
            @RequestParam(required = false) Double south,
            @RequestParam(required = false) Double east,
            @RequestParam(required = false) Double west,
            @RequestParam(required = false) String searchName,
            @RequestParam(required = false) String searchLocation,
            @RequestParam(required = false) String searchTreatment,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        List<Center> centers = centerRepository.findAll();

        // 0. Bounding Box Filter (Map View)
        if (north != null && south != null && east != null && west != null) {
            centers = centers.stream().filter(c -> {
                if (c.getLatitude() == null || c.getLongitude() == null) return false;
                boolean latInRange = c.getLatitude() <= north && c.getLatitude() >= south;
                // Handle dateline crossing for longitude if needed, but for simplicity:
                boolean lonInRange = (west <= east) ? 
                    (c.getLongitude() >= west && c.getLongitude() <= east) :
                    (c.getLongitude() >= west || c.getLongitude() <= east);
                return latInRange && lonInRange;
            }).collect(Collectors.toList());
        }

        // 1. Filters
        if (searchName != null && !searchName.trim().isEmpty()) {
            String q = searchName.toLowerCase().trim();
            centers = centers.stream().filter(c -> 
                (c.getName() != null && c.getName().toLowerCase().contains(q)) || 
                (c.getAddress() != null && c.getAddress().toLowerCase().contains(q))
            ).collect(Collectors.toList());
        }
        if (searchLocation != null && !searchLocation.trim().isEmpty()) {
            String q = searchLocation.toLowerCase().trim();
            centers = centers.stream().filter(c -> c.getAddress() != null && c.getAddress().toLowerCase().contains(q)).collect(Collectors.toList());
        }
        if (searchTreatment != null && !searchTreatment.trim().isEmpty()) {
            try {
                com.alexxlpz.crm_cbelleza.entities.TreatmentType type = 
                    com.alexxlpz.crm_cbelleza.entities.TreatmentType.valueOf(searchTreatment.toUpperCase());
                centers = centers.stream().filter(c -> {
                    List<Treatment> treatments = treatmentRepository.findByCenterId(c.getId());
                    return treatments.stream().anyMatch(t -> t.getType() == type);
                }).collect(Collectors.toList());
            } catch (Exception e) {
                // Ignore if invalid type
            }
        }

        // 2. Map to DTO and calculate distance
        List<CenterDTO> dtos = centers.stream().map(c -> {
            Double distance = null;
            if (lat != null && lon != null && c.getLatitude() != null && c.getLongitude() != null) {
                distance = calculateHaversineDistance(lat, lon, c.getLatitude(), c.getLongitude());
            }
            return new CenterDTO(c, distance);
        }).collect(Collectors.toList());

        // 3. Sort by distance (if available), then by name
        dtos.sort((a, b) -> {
            if (a.distanceKm != null && b.distanceKm != null) {
                return Double.compare(a.distanceKm, b.distanceKm);
            }
            if (a.distanceKm != null) return -1;
            if (b.distanceKm != null) return 1;
            return a.name.compareToIgnoreCase(b.name);
        });

        // 4. Pagination
        int start = page * size;
        if (start >= dtos.size()) {
            return List.of();
        }
        int end = Math.min(start + size, dtos.size());
        return dtos.subList(start, end);
    }

    private double calculateHaversineDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // Radius of the earth in km
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c; 
    }
}
