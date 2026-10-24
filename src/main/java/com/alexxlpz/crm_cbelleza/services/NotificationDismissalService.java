package com.alexxlpz.crm_cbelleza.services;

import com.alexxlpz.crm_cbelleza.entities.NotificationDismissal;
import com.alexxlpz.crm_cbelleza.repositories.NotificationDismissalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Avisos de la campana que cada usuario ha quitado: «Marcar como visto» en uno o «Limpiar todas». */
@Service
public class NotificationDismissalService {

    /** Tope de avisos por petición («Limpiar todas» manda los que hay en la campana). */
    static final int MAX_KEYS = 100;

    private final NotificationDismissalRepository dismissals;
    private final Clock clock;

    public NotificationDismissalService(NotificationDismissalRepository dismissals, Clock clock) {
        this.dismissals = dismissals;
        this.clock = clock;
    }

    /**
     * Los avisos que el usuario no ha quitado. De paso olvida los que quitó y ya no salen,
     * para que si un día vuelve a darse el mismo aviso se vea otra vez.
     */
    @Transactional
    public <T> List<T> visible(Long userId, List<T> items, Function<T, String> key) {
        List<String> dismissed = dismissals.findKeysByUserId(userId);
        if (dismissed.isEmpty()) {
            return items;
        }
        Set<String> current = items.stream().map(key).collect(Collectors.toSet());
        Set<String> stale = dismissed.stream().filter(k -> !current.contains(k)).collect(Collectors.toSet());
        if (!stale.isEmpty()) {
            dismissals.deleteByUserIdAndKeys(userId, stale);
        }
        Set<String> hidden = new HashSet<>(dismissed);
        return items.stream().filter(item -> !hidden.contains(key.apply(item))).toList();
    }

    /** Quita de la campana del usuario los avisos con estas huellas (las que no son válidas se ignoran). */
    @Transactional
    public int dismiss(Long userId, Collection<String> keys) {
        Set<String> wanted = keys.stream().filter(NotificationKeys::isValid).limit(MAX_KEYS).collect(Collectors.toSet());
        if (wanted.isEmpty()) {
            return 0;
        }
        wanted.removeAll(dismissals.findKeysByUserId(userId));
        LocalDateTime now = LocalDateTime.now(clock);
        dismissals.saveAll(wanted.stream()
                .map(k -> NotificationDismissal.builder().userId(userId).notificationKey(k).dismissedAt(now).build())
                .toList());
        return wanted.size();
    }
}
