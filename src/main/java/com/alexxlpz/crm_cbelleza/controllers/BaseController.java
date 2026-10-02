package com.alexxlpz.crm_cbelleza.controllers;

import com.alexxlpz.crm_cbelleza.entities.*;
import com.alexxlpz.crm_cbelleza.repositories.*;
import com.alexxlpz.crm_cbelleza.services.CenterService;
import com.alexxlpz.crm_cbelleza.services.EmailService;
import com.alexxlpz.crm_cbelleza.services.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.List;
import java.util.Optional;

@Controller
public class BaseController {

    private final CenterRepository centerRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final CenterService centerService;
    private final EmailService emailService;

    public BaseController(CenterRepository centerRepository, UserRepository userRepository, UserService userService, CenterService centerService, EmailService emailService) {
        this.centerRepository = centerRepository;
        this.userRepository = userRepository;
        this.userService = userService;
        this.centerService = centerService;
        this.emailService = emailService;
    }

    @GetMapping("/")
    public String index(HttpSession session) {
        String role = (String) session.getAttribute("sessionRole");
        if ("CLIENT".equals(role)) {
            return "redirect:/centers";
        } else if ("WORKER".equals(role)) {
            return "redirect:/worker/dashboard";
        }
        return "redirect:/home";
    }

    @GetMapping("/home")
    public String landing(HttpSession session, Model model) {
        model.addAttribute("sessionRole", session.getAttribute("sessionRole"));
        model.addAttribute("sessionUserName", session.getAttribute("sessionUserName"));
        model.addAttribute("activePage", "home");
        return "landing";
    }

    @GetMapping("/centers")
    public String publicCenters(@RequestParam(value = "searchLocation", required = false) String searchLocation,
                                HttpSession session, Model model) {
        model.addAttribute("sessionRole", session.getAttribute("sessionRole"));
        model.addAttribute("sessionUserName", session.getAttribute("sessionUserName"));
        model.addAttribute("activePage", "centers");

        List<Center> centers = centerService.getCentersFilteredByLocation(searchLocation);
        if (searchLocation != null && !searchLocation.trim().isEmpty()) {
            model.addAttribute("currentSearchLocation", searchLocation);
        }
        model.addAttribute("centers", centers);
        return "centers";
    }

    @GetMapping({"/features", "/funcionalidades"})
    public String features(HttpSession session, Model model) {
        model.addAttribute("sessionRole", session.getAttribute("sessionRole"));
        model.addAttribute("sessionUserName", session.getAttribute("sessionUserName"));
        model.addAttribute("activePage", "features");
        return "features";
    }

    @GetMapping({"/register-center", "/alta-centro"})
    public String registerCenter(HttpSession session, Model model) {
        model.addAttribute("sessionRole", session.getAttribute("sessionRole"));
        model.addAttribute("sessionUserName", session.getAttribute("sessionUserName"));
        model.addAttribute("activePage", "register-center");
        return "register-center";
    }

    @PostMapping({"/register-center", "/alta-centro"})
    public String registerCenterSubmit(@RequestParam("centerName") String centerName,
                                       @RequestParam("cif") String cif,
                                       @RequestParam("contactName") String contactName,
                                       @RequestParam("phone") String phone,
                                       @RequestParam("email") String email,
                                       @RequestParam(value = "city", required = false) String city,
                                       @RequestParam(value = "specialties", required = false) List<String> specialties,
                                       @RequestParam(value = "notes", required = false) String notes,
                                       RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("successMessage",
                "¡Solicitud enviada con éxito! Nuestro departamento de acreditación revisará los datos de " + centerName +
                " y se pondrá en contacto contigo en un plazo máximo de 24 horas para verificar y certificar tu centro.");
        return "redirect:/register-center";
    }

    @GetMapping({"/contact", "/contacto"})
    public String contact(HttpSession session, Model model) {
        model.addAttribute("sessionRole", session.getAttribute("sessionRole"));
        model.addAttribute("sessionUserName", session.getAttribute("sessionUserName"));
        model.addAttribute("activePage", "contact");
        return "contact";
    }

    @PostMapping({"/contact", "/contacto"})
    public String contactSubmit(@RequestParam("name") String name,
                                @RequestParam("email") String email,
                                @RequestParam(value = "phone", required = false) String phone,
                                @RequestParam("subject") String subject,
                                @RequestParam("message") String message,
                                RedirectAttributes redirectAttributes) {
        emailService.sendContactInquiryAsync(name, email, phone, subject, message);
        redirectAttributes.addFlashAttribute("successMessage",
                "Gracias por contactar con nosotros, " + name + ". Hemos recibido tu mensaje y te responderemos a la mayor brevedad posible.");
        return "redirect:/contact";
    }

    @GetMapping("/register")
    public String registerPage(HttpSession session, Model model) {
        String activeRole = (String) session.getAttribute("sessionRole");
        if (activeRole != null) {
            if ("CLIENT".equals(activeRole)) return "redirect:/centers";
            if ("WORKER".equals(activeRole)) return "redirect:/worker/dashboard";
        }
        return "register";
    }

    @PostMapping("/register")
    public String registerSubmit(@RequestParam("name") String name,
                                 @RequestParam("email") String email,
                                 @RequestParam(value = "phone", required = false) String phone,
                                 @RequestParam("password") String password,
                                 @RequestParam("confirmPassword") String confirmPassword,
                                 HttpSession session,
                                 RedirectAttributes redirectAttributes) {

        if (name == null || name.trim().isEmpty() ||
            email == null || email.trim().isEmpty() ||
            password == null || password.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Por favor, completa todos los campos requeridos.");
            redirectAttributes.addFlashAttribute("name", name);
            redirectAttributes.addFlashAttribute("email", email);
            redirectAttributes.addFlashAttribute("phone", phone);
            return "redirect:/register";
        }

        if (password.length() < 6) {
            redirectAttributes.addFlashAttribute("errorMessage", "La contraseña debe tener al menos 6 caracteres.");
            redirectAttributes.addFlashAttribute("name", name);
            redirectAttributes.addFlashAttribute("email", email);
            redirectAttributes.addFlashAttribute("phone", phone);
            return "redirect:/register";
        }

        if (!password.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Las contraseñas no coinciden. Por favor, revísalas.");
            redirectAttributes.addFlashAttribute("name", name);
            redirectAttributes.addFlashAttribute("email", email);
            redirectAttributes.addFlashAttribute("phone", phone);
            return "redirect:/register";
        }

        String cleanEmail = email.trim().toLowerCase();
        if (userService.findByEmail(cleanEmail).isPresent()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Este correo electrónico ya está registrado. Por favor, inicia sesión.");
            redirectAttributes.addFlashAttribute("name", name);
            redirectAttributes.addFlashAttribute("phone", phone);
            return "redirect:/register";
        }

        User newUser = userService.registerUser(name.trim(), cleanEmail, phone != null && !phone.trim().isEmpty() ? phone.trim() : null, password, Role.CLIENT, null);

        // Iniciar sesión automáticamente para el nuevo cliente
        session.setAttribute("sessionRole", "CLIENT");
        session.setAttribute("sessionUserId", newUser.getId());
        session.setAttribute("sessionUserName", newUser.getName());
        session.removeAttribute("sessionCenterId");
        session.removeAttribute("sessionCenterName");

        return "redirect:/centers";
    }

    @GetMapping("/login")
    public String loginPage(@RequestParam(value = "error", required = false) String error,
                            @RequestParam(value = "logout", required = false) String logout,
                            Model model,
                            HttpSession session) {
        String activeRole = (String) session.getAttribute("sessionRole");
        if (activeRole != null) {
            if ("CLIENT".equals(activeRole)) return "redirect:/centers";
            if ("WORKER".equals(activeRole)) return "redirect:/worker/dashboard";
        }

        if (error != null) {
            model.addAttribute("errorMessage", "Credenciales incorrectas. Verifica tu usuario/correo y contraseña.");
        }
        if (logout != null) {
            model.addAttribute("successMessage", "Has cerrado sesión correctamente.");
        }

        return "login";
    }

    @PostMapping("/login")
    public String loginSubmit(@RequestParam("identifier") String identifier,
                              @RequestParam("password") String password,
                              @RequestParam(value = "rememberMe", required = false) Boolean rememberMe,
                              HttpSession session,
                              RedirectAttributes redirectAttributes) {

        if (identifier == null || identifier.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Por favor, introduce tu usuario/correo y contraseña.");
            redirectAttributes.addFlashAttribute("identifier", identifier);
            return "redirect:/login";
        }

        Optional<User> userOpt = userService.authenticate(identifier, password);
        if (userOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Credenciales incorrectas. Verifica tu usuario/correo y contraseña.");
            redirectAttributes.addFlashAttribute("identifier", identifier != null ? identifier.trim() : "");
            return "redirect:/login";
        }

        User user = userOpt.get();
        if (user.getRole() == Role.CLIENT) {
            session.setAttribute("sessionRole", "CLIENT");
            session.setAttribute("sessionUserId", user.getId());
            session.setAttribute("sessionUserName", user.getName());
            session.removeAttribute("sessionCenterId");
            session.removeAttribute("sessionCenterName");
            return "redirect:/centers";
        } else if (user.getRole() == Role.WORKER) {
            session.setAttribute("sessionRole", "WORKER");
            session.setAttribute("sessionUserId", user.getId());
            session.setAttribute("sessionUserName", user.getName());
            if (user.getCenter() != null) {
                session.setAttribute("sessionCenterId", user.getCenter().getId());
                session.setAttribute("sessionCenterName", user.getCenter().getName());
            }
            return "redirect:/worker/dashboard";
        }

        return "redirect:/home";
    }

    @GetMapping("/login-selector")
    public String loginSelectorPage(Model model, HttpSession session) {
        String activeRole = (String) session.getAttribute("sessionRole");
        if (activeRole != null) {
            if ("CLIENT".equals(activeRole)) return "redirect:/centers";
            if ("WORKER".equals(activeRole)) return "redirect:/worker/dashboard";
        }

        List<User> clients = userRepository.findByRole(Role.CLIENT);
        List<User> workers = userRepository.findByRole(Role.WORKER);

        model.addAttribute("clients", clients);
        model.addAttribute("workers", workers);

        return "login-selector";
    }

    @RequestMapping(value = "/select-session", method = {RequestMethod.GET, RequestMethod.POST})
    public String selectSession(@RequestParam("role") String role,
                                @RequestParam(value = "userId", required = false) Long userId,
                                @RequestParam(value = "centerId", required = false) Long centerId,
                                HttpSession session) {
        
        if ("CLIENT".equals(role)) {
            if (userId != null) {
                User client = userRepository.findById(userId).orElse(null);
                if (client != null && client.getRole() == Role.CLIENT) {
                    session.setAttribute("sessionRole", "CLIENT");
                    session.setAttribute("sessionUserId", client.getId());
                    session.setAttribute("sessionUserName", client.getName());
                    session.removeAttribute("sessionCenterId");
                    session.removeAttribute("sessionCenterName");
                    return "redirect:/centers";
                }
            }
            // Guest mode has been removed: clients must have an account
            return "redirect:/login";
            
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
