package com.alexxlpz.crm_cbelleza.services;

import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.repositories.CenterRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CenterService {
    private final CenterRepository centerRepository;

    public CenterService(CenterRepository centerRepository) {
        this.centerRepository = centerRepository;
    }

    public List<Center> getCentersFilteredByLocation(String searchLocation) {
        List<Center> centers = centerRepository.findAll();
        if (searchLocation != null && !searchLocation.trim().isEmpty()) {
            String query = searchLocation.toLowerCase().trim();
            return centers.stream()
                    .filter(c -> c.getName().toLowerCase().contains(query) || 
                                 c.getAddress().toLowerCase().contains(query))
                    .collect(Collectors.toList());
        }
        return centers;
    }
    
    public Center getCenterById(Long id) {
        return centerRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Invalid center ID: " + id));
    }
}
