package com.alexxlpz.crm_cbelleza.services;

import com.alexxlpz.crm_cbelleza.booking.CenterTimeZones;
import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;
import com.alexxlpz.crm_cbelleza.exceptions.ResourceNotFoundException;
import com.alexxlpz.crm_cbelleza.forms.CenterForm;
import com.alexxlpz.crm_cbelleza.forms.FormText;
import com.alexxlpz.crm_cbelleza.repositories.CenterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Datos de los centros. */
@Service
@Transactional
public class CenterService {

    /** Límite razonable de cabinas o puestos de un centro. */
    public static final int MAX_CABINS = 50;

    private final CenterRepository centerRepository;

    public CenterService(CenterRepository centerRepository) {
        this.centerRepository = centerRepository;
    }

    @Transactional(readOnly = true)
    public Center getCenterById(Long id) {
        return centerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("El centro solicitado no existe."));
    }

    /**
     * Enlace corto del centro para Instagram, Google Maps o WhatsApp: {@code /c/centro-historico}. Sale del nombre
     * (nunca del id) y se guarda en {@code centers.slug} en cuanto existe el centro: después no cambia aunque se renombre
     * el centro o se dé de alta otro con el mismo nombre, que recibe {@code /c/centro-historico-2}. Si se recalculase en
     * cada visita, renombrar un centro antiguo le quitaría el enlace a otro (S-03 del reporte de seguridad).
     */
    @Transactional(readOnly = true)
    public String shortSlug(Center center) {
        return center.getSlug() != null ? center.getSlug() : shortSlugs().get(center.getId());
    }

    /** Centro publicado por su nombre en la dirección (el de {@link #shortSlug}); 404 si no hay ninguno. */
    @Transactional(readOnly = true)
    public Center getPublishedBySlug(String slug) {
        return findPublishedBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("El centro solicitado no existe."));
    }

    /** El centro publicado de un enlace corto; vacío si no hay ninguno o aún no se ha publicado. */
    @Transactional(readOnly = true)
    public Optional<Center> findPublishedBySlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return Optional.empty();
        }
        String clean = slug.toLowerCase(Locale.ROOT);
        Optional<Center> stored = centerRepository.findBySlug(clean);
        if (stored.isPresent()) {
            return stored.filter(Center::isPublished);
        }
        // Centros que aún no tienen el enlace guardado (se guarda al arrancar la app, ver assignMissingSlugs)
        Map<Long, String> slugs = shortSlugs();
        return centerRepository.findAll().stream()
                .filter(center -> center.getSlug() == null && center.isPublished() && clean.equals(slugs.get(center.getId())))
                .findFirst();
    }

    /**
     * Enlace corto de cada centro, por id: el guardado o, si aún no tiene, el que le tocaría (el nombre limpio o con
     * -2, -3... si ya está cogido, por orden de alta).
     */
    @Transactional(readOnly = true)
    public Map<Long, String> shortSlugs() {
        List<Center> all = centerRepository.findAll();
        Map<Long, String> slugs = new HashMap<>();
        Set<String> taken = new HashSet<>();
        all.stream().filter(c -> c.getSlug() != null).forEach(c -> {
            slugs.put(c.getId(), c.getSlug());
            taken.add(c.getSlug());
        });
        all.stream()
                .filter(c -> c.getSlug() == null)
                .sorted(Comparator.comparing(Center::getId))
                .forEach(center -> {
                    String slug = freeSlug(center.getName(), taken);
                    taken.add(slug);
                    slugs.put(center.getId(), slug);
                });
        return slugs;
    }

    /** Guarda el enlace de un centro nuevo: su nombre o, si ya lo tiene otro (aunque sea sin publicar), con -2, -3... */
    public Center assignSlug(Center center) {
        if (center.getSlug() == null) {
            Map<Long, String> slugs = shortSlugs();
            slugs.remove(center.getId());
            Set<String> taken = new HashSet<>(slugs.values());
            center.setSlug(freeSlug(center.getName(), taken));
            centerRepository.save(center);
        }
        return center;
    }

    /** Guarda el enlace de los centros que no lo tienen (los anteriores a V26 y los datos de demostración). */
    public int assignMissingSlugs() {
        Map<Long, String> slugs = shortSlugs();
        List<Center> missing = centerRepository.findAll().stream().filter(c -> c.getSlug() == null).toList();
        missing.forEach(center -> center.setSlug(slugs.get(center.getId())));
        centerRepository.saveAll(missing);
        return missing.size();
    }

    private static String freeSlug(String name, Set<String> taken) {
        String base = slugOf(name);
        if (base.isEmpty()) {
            base = "centro";
        }
        if (base.length() > 100) {
            base = base.substring(0, 100).replaceAll("-+$", "");
        }
        String slug = base;
        for (int n = 2; taken.contains(slug); n++) {
            slug = base + "-" + n;
        }
        return slug;
    }

    /** Nombre en la dirección de los centros publicados, por orden de alta (para el sitemap). */
    @Transactional(readOnly = true)
    public List<String> publishedSlugs() {
        Map<Long, String> slugs = shortSlugs();
        return centerRepository.findAll().stream()
                .filter(Center::isPublished)
                .sorted(Comparator.comparing(Center::getId))
                .map(center -> slugs.get(center.getId()))
                .toList();
    }

    /** «Peluquería Ana & Co.» → «peluqueria-ana-co»: minúsculas, sin tildes y con guiones. */
    public static String slugOf(String name) {
        if (name == null) {
            return "";
        }
        String plain = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return plain.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
    }

    /** Centro visible para los clientes: uno pendiente de revisión responde como si no existiera. */
    @Transactional(readOnly = true)
    public Center getPublishedCenter(Long id) {
        Center center = getCenterById(id);
        if (!center.isPublished()) {
            throw new ResourceNotFoundException("El centro solicitado no existe.");
        }
        return center;
    }

    /** Activa o desactiva la confirmación automática de las citas que piden los clientes. */
    public Center setAutoAcceptBookings(Long centerId, boolean enabled) {
        Center center = getCenterById(centerId);
        center.setAutoAcceptBookings(enabled);
        return centerRepository.save(center);
    }

    /** Plazos que puede elegir el dueño para que el cliente cambie la hora él mismo (0 = no puede). */
    public static final List<Integer> CLIENT_RESCHEDULE_OPTIONS = List.of(0, 2, 12, 24, 48);

    /** Hasta cuántas horas antes de la cita puede el cliente cambiarla desde «Mis reservas». */
    public Center setClientRescheduleHours(Long centerId, int hours) {
        if (!CLIENT_RESCHEDULE_OPTIONS.contains(hours)) {
            throw new BusinessRuleException("Elige uno de los plazos de la lista.");
        }
        Center center = getCenterById(centerId);
        center.setClientRescheduleHours(hours);
        return centerRepository.save(center);
    }

    /** Actualiza solo los campos informados; un nombre vacío no borra el actual. */
    public Center updateCenter(Long centerId, CenterForm form) {
        Center center = getCenterById(centerId);
        if (!FormText.isBlank(form.name())) {
            center.setName(form.name().trim());
        }
        if (form.address() != null) center.setAddress(form.address().trim());
        if (form.phone() != null) center.setPhone(form.phone().trim());
        if (form.email() != null) center.setEmail(form.email().trim());
        if (form.latitude() != null) center.setLatitude(form.latitude());
        if (form.longitude() != null) center.setLongitude(form.longitude());
        if (form.timeZone() != null) {
            if (!CenterTimeZones.isValid(form.timeZone())) {
                throw new BusinessRuleException("Elige una zona horaria de la lista.");
            }
            center.setTimeZone(form.timeZone());
        }
        if (form.cabins() != null) {
            if (form.cabins() < 1 || form.cabins() > MAX_CABINS) {
                throw new BusinessRuleException("El número de cabinas tiene que estar entre 1 y " + MAX_CABINS + ".");
            }
            center.setCabins(form.cabins());
        }
        return centerRepository.save(center);
    }
}
