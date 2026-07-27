package com.alexxlpz.crm_cbelleza.controllers;

import com.alexxlpz.crm_cbelleza.entities.*;
import com.alexxlpz.crm_cbelleza.repositories.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@Controller
public class BaseController {

    private final CenterRepository centerRepository;
    private final UserRepository userRepository;

    public BaseController(CenterRepository centerRepository, UserRepository userRepository) {
        this.centerRepository = centerRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/")
    public String index(HttpSession session) {
        String role = (String) session.getAttribute("sessionRole");
        if ("CLIENT".equals(role)) {
            return "redirect:/client/centers";
        } else if ("WORKER".equals(role)) {
            return "redirect:/worker/dashboard";
        }
        return "redirect:/home";
    }

    @GetMapping("/home")
    public String landing(HttpSession session, Model model) {
        // Pass current session info for dynamic layouts
        model.addAttribute("sessionRole", session.getAttribute("sessionRole"));
        model.addAttribute("sessionUserName", session.getAttribute("sessionUserName"));
        return "landing";
    }

    @GetMapping("/login")
    public String loginPage(Model model, HttpSession session) {
        String activeRole = (String) session.getAttribute("sessionRole");
        if (activeRole != null) {
            if ("CLIENT".equals(activeRole)) return "redirect:/client/centers";
            if ("WORKER".equals(activeRole)) return "redirect:/worker/dashboard";
        }

        List<User> clients = userRepository.findByRole(Role.CLIENT);
        List<User> workers = userRepository.findByRole(Role.WORKER);

        model.addAttribute("clients", clients);
        model.addAttribute("workers", workers);

        return "login";
    }

    @PostMapping("/select-session")
    public String selectSession(@RequestParam("role") String role,
                                @RequestParam(value = "userId", required = false) Long userId,
                                @RequestParam(value = "centerId", required = false) Long centerId,
                                HttpSession session) {
        
        if ("CLIENT".equals(role)) {
            session.setAttribute("sessionRole", "CLIENT");
            if (userId != null) {
                User client = userRepository.findById(userId).orElse(null);
                if (client != null) {
                    session.setAttribute("sessionUserId", client.getId());
                    session.setAttribute("sessionUserName", client.getName());
                }
            } else {
                // Anonymous Guest Client mode
                session.setAttribute("sessionUserId", null);
                session.setAttribute("sessionUserName", "Invitado");
            }
            session.removeAttribute("sessionCenterId");
            session.removeAttribute("sessionCenterName");
            return "redirect:/client/centers";
            
        } else if ("WORKER".equals(role)) {
            session.setAttribute("sessionRole", "WORKER");
            if (userId != null) {
                User worker = userRepository.findById(userId).orElse(null);
                if (worker != null) {
                    session.setAttribute("sessionUserId", worker.getId());
                    session.setAttribute("sessionUserName", worker.getName());
                    if (worker.getCenter() != null) {
                        session.setAttribute("sessionCenterId", worker.getCenter().getId());
                        session.setAttribute("sessionCenterName", worker.getCenter().getName());
                    }
                }
            } else if (centerId != null) {
                Center center = centerRepository.findById(centerId).orElse(null);
                session.setAttribute("sessionUserId", 3L); // Default worker (Carlos)
                session.setAttribute("sessionUserName", "Carlos Mendoza");
                if (center != null) {
                    session.setAttribute("sessionCenterId", center.getId());
                    session.setAttribute("sessionCenterName", center.getName());
                }
            }
            return "redirect:/worker/dashboard";
        }
        
        return "redirect:/home";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/home";
    }
}
