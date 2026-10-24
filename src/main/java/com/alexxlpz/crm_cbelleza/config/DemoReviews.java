package com.alexxlpz.crm_cbelleza.config;

import com.alexxlpz.crm_cbelleza.entities.Appointment;
import com.alexxlpz.crm_cbelleza.entities.AppointmentStatus;
import com.alexxlpz.crm_cbelleza.entities.Review;
import com.alexxlpz.crm_cbelleza.repositories.ReviewRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Reseñas de ejemplo sobre las visitas completadas del historial demo (una de cada cinco), con respuestas del
 * dueño en casi todas. Las tres más recientes se quedan sin responder y los clientes de siempre (los de las
 * pruebas) no valoran su último mes, para poder probar «Valorar» desde «Mis citas».
 */
final class DemoReviews {

    private static final String[][] COMMENTS = {
            {},
            {"No me gustó nada: llegué a la hora y me tuvieron esperando casi media hora.",
                    "El resultado no fue el que pedí y nadie me ofreció arreglarlo."},
            {"El trato fue correcto, pero el resultado no me duró ni una semana.",
                    "Bien sin más. Para el precio esperaba bastante más."},
            {"Correcto. El centro está muy bien, aunque fueron algo justos de tiempo.",
                    "Buen resultado, pero el ambiente era bastante ruidoso.",
                    null},
            {"Muy contenta con el resultado. Repetiré seguro.",
                    "Profesionales y puntuales. Le quito una estrella porque el aparcamiento es complicado.",
                    "Me explicaron todo muy bien y el resultado es muy natural.",
                    null},
            {"¡Encantada! Me atendieron genial desde el primer momento y el resultado es perfecto.",
                    "Llevo meses viniendo y nunca falla. Un trato de diez.",
                    "Súper detallistas y el centro es precioso. Salí como nueva.",
                    "La mejor manicura que me han hecho, y me duró casi tres semanas.",
                    "Muy limpio, puntuales y con muchísimo cariño. Totalmente recomendable.",
                    null}
    };

    private static final String[] REPLIES_GOOD = {
            "¡Muchísimas gracias, %s! Nos alegra un montón que salieras contenta. ¡Te esperamos pronto!",
            "Gracias a ti por confiar en nosotros, %s. Da gusto leer opiniones así.",
            "¡Qué alegría leerte, %s! Se lo diremos a todo el equipo."
    };

    private static final String[] REPLIES_BAD = {
            "Sentimos mucho tu experiencia, %s. Nos gustaría compensarte: llámanos y lo hablamos con calma.",
            "Gracias por contárnoslo, %s. Ya lo hemos hablado con el equipo para que no vuelva a pasar."
    };

    private final ReviewRepository reviews;
    private final Random random = new Random(20261024L);

    DemoReviews(ReviewRepository reviews) {
        this.reviews = reviews;
    }

    void seed(List<Appointment> history, Set<Long> regularIds, LocalDateTime today) {
        List<Review> created = new ArrayList<>();
        for (Appointment appointment : history) {
            boolean regularsLastMonth = regularIds.contains(appointment.getClient().getId())
                    && appointment.getDateTime().isAfter(today.minusDays(30));
            if (appointment.getStatus() != AppointmentStatus.COMPLETED || regularsLastMonth || random.nextInt(5) != 0) {
                continue;
            }
            int rating = rating();
            String[] options = COMMENTS[rating];
            LocalDateTime createdAt = appointment.getDateTime().plusHours(3 + random.nextInt(60));
            if (!createdAt.isBefore(today)) {
                createdAt = today.minusHours(1 + random.nextInt(5));
            }
            created.add(Review.builder()
                    .appointment(appointment)
                    .center(appointment.getCenter())
                    .client(appointment.getClient())
                    .rating(rating)
                    .comment(options[random.nextInt(options.length)])
                    .createdAt(createdAt)
                    .build());
        }
        created.sort(java.util.Comparator.comparing(Review::getCreatedAt));
        for (int i = 0; i < created.size() - 3; i++) {
            Review review = created.get(i);
            if (random.nextInt(10) < 8) {
                String first = review.getAuthorName().split(" ")[0];
                String[] replies = review.getRating() >= 4 ? REPLIES_GOOD : REPLIES_BAD;
                review.setReply(String.format(replies[random.nextInt(replies.length)], first));
                review.setRepliedAt(review.getCreatedAt().plusHours(2 + random.nextInt(40)));
            }
        }
        reviews.saveAll(created);
    }

    /** Sobre todo cincos y cuatros, como en la vida real; alguna mala de vez en cuando. */
    private int rating() {
        int n = random.nextInt(100);
        if (n < 58) return 5;
        if (n < 86) return 4;
        if (n < 95) return 3;
        if (n < 98) return 2;
        return 1;
    }
}
