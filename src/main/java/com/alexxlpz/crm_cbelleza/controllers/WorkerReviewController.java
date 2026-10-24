package com.alexxlpz.crm_cbelleza.controllers;

import com.alexxlpz.crm_cbelleza.entities.Review;
import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;
import com.alexxlpz.crm_cbelleza.security.AppUserDetails;
import com.alexxlpz.crm_cbelleza.security.CenterOwnerOnly;
import com.alexxlpz.crm_cbelleza.services.ReviewService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/** Reseñas del centro: todo el equipo las lee; responder en público es cosa del dueño. */
@Controller
public class WorkerReviewController {

    private static final String UNANSWERED = "sin-responder";

    private final ReviewService reviewService;

    public WorkerReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping("/worker/reviews")
    public String reviews(@AuthenticationPrincipal AppUserDetails worker,
                          @RequestParam(name = "filtro", required = false) String filter,
                          Model model) {
        Long centerId = worker.getCenterId();
        List<Review> all = reviewService.forCenter(centerId);
        boolean onlyUnanswered = UNANSWERED.equals(filter);
        model.addAttribute("reviews", onlyUnanswered ? all.stream().filter(review -> !review.hasReply()).toList() : all);
        model.addAttribute("onlyUnanswered", onlyUnanswered);
        model.addAttribute("totalCount", all.size());
        model.addAttribute("unansweredCount", all.stream().filter(review -> !review.hasReply()).count());
        model.addAttribute("rating", reviewService.rating(centerId));
        model.addAttribute("ratingDistribution", reviewService.distribution(centerId));
        return "worker/reviews";
    }

    @CenterOwnerOnly
    @PostMapping("/worker/reviews/{id}/reply")
    public String reply(@AuthenticationPrincipal AppUserDetails worker,
                        @PathVariable Long id,
                        @RequestParam(required = false) String reply,
                        @RequestParam(name = "filtro", required = false) String filter,
                        RedirectAttributes redirect) {
        try {
            Review review = reviewService.reply(worker.getCenterId(), id, reply);
            redirect.addFlashAttribute("successMessage", review.hasReply()
                    ? "Respuesta publicada. " + review.getAuthorName() + " y el resto de clientes ya pueden verla."
                    : "Respuesta quitada.");
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return UNANSWERED.equals(filter) ? "redirect:/worker/reviews?filtro=" + UNANSWERED : "redirect:/worker/reviews#resena-" + id;
    }
}
