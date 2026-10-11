package com.alexxlpz.crm_cbelleza.services;

import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.entities.Role;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;
import com.alexxlpz.crm_cbelleza.exceptions.ResourceNotFoundException;
import com.alexxlpz.crm_cbelleza.forms.FormText;
import com.alexxlpz.crm_cbelleza.forms.PasswordPolicy;
import com.alexxlpz.crm_cbelleza.forms.ProfileForm;
import com.alexxlpz.crm_cbelleza.forms.RegistrationForm;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import com.alexxlpz.crm_cbelleza.security.AppUserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Cuentas de usuario: registro, perfil y consultas de personal. */
@Service
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final PersistentTokenRepository rememberMeTokens;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, PasswordPolicy passwordPolicy,
                       PersistentTokenRepository rememberMeTokens) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.rememberMeTokens = rememberMeTokens;
    }

    @Transactional(readOnly = true)
    public User getUser(Long id) {
        return userRepository.findWithCenterById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado."));
    }

    @Transactional(readOnly = true)
    public List<User> getWorkersByCenter(Long centerId) {
        return userRepository.findByCenterIdAndRole(centerId, Role.WORKER);
    }

    @Transactional(readOnly = true)
    public long countWorkersByCenter(Long centerId) {
        return userRepository.countByCenterIdAndRole(centerId, Role.WORKER);
    }

    /**
     * Alta pública de un cliente. Si un centro ya lo había dado de alta a mano con ese correo (cuenta sin
     * contraseña ni Google), el registro completa esa cuenta en vez de rechazarse: conserva sus citas y fichas,
     * y los datos que escribe el cliente sustituyen a los que puso el centro. Una cuenta de Google ya tiene
     * dueño aunque no tenga contraseña: no se puede registrar encima (la contraseña se añade desde el perfil).
     * Lanza {@link BusinessRuleException} con un mensaje para el usuario si algo falla.
     */
    public User registerClient(RegistrationForm form) {
        if (FormText.isBlank(form.name()) || FormText.isBlank(form.email())) {
            throw new BusinessRuleException("Por favor, completa todos los campos requeridos.");
        }
        passwordPolicy.validateRequired(form.password(), form.confirmPassword());
        String email = form.normalizedEmail();
        if (email.endsWith(User.PLACEHOLDER_EMAIL_DOMAIN)) {
            // Correos internos inventados a partir del teléfono: nadie puede registrarse (ni reclamar uno) con ellos
            throw new BusinessRuleException("Introduce un correo electrónico válido.");
        }
        var existing = userRepository.findByEmailIgnoreCase(email);
        if (existing.isEmpty()) {
            return createUser(form.name().trim(), email, FormText.trimToNull(form.phone()), form.password(), Role.CLIENT, null);
        }
        User user = existing.get();
        if (user.getPassword() == null && user.getGoogleId() != null) {
            throw new BusinessRuleException("Ya existe una cuenta con este correo y se entra con Google. "
                    + "Pulsa «Continuar con Google» para acceder; si quieres entrar también con contraseña, "
                    + "añádela desde tu perfil.");
        }
        if (user.hasAccount() || user.getRole() != Role.CLIENT) {
            throw new BusinessRuleException("Ya existe una cuenta con este correo electrónico. Por favor, inicia sesión.");
        }
        // Prevalece lo que escribe el cliente; el teléfono del centro solo se mantiene si no pone ninguno
        user.setName(form.name().trim());
        user.setEmail(email);
        String phone = FormText.trimToNull(form.phone());
        if (phone != null) {
            user.setPhone(phone);
        }
        user.setPassword(passwordEncoder.encode(form.password()));
        return userRepository.save(user);
    }

    /**
     * Inicio de sesión o alta con Google. Busca la cuenta ya vinculada a ese Google; si no la hay, vincula
     * la cuenta que tenga el mismo correo (Google ya ha verificado que es suyo, así que también un cliente
     * creado a mano desde la cartera pasa a tener cuenta) y, si tampoco existe, crea un cliente nuevo sin contraseña.
     *
     * @param googleId      identificador estable de Google (claim "sub")
     * @param emailVerified si Google confirma que el correo pertenece al usuario
     */
    public User signInWithGoogle(String googleId, String email, boolean emailVerified, String name) {
        if (FormText.isBlank(googleId)) {
            throw new BusinessRuleException("Google no ha devuelto los datos de tu cuenta. Inténtalo de nuevo.");
        }
        var linked = userRepository.findByGoogleId(googleId);
        if (linked.isPresent()) {
            return linked.get();
        }
        if (!emailVerified || FormText.isBlank(email)) {
            throw new BusinessRuleException(
                    "Tu cuenta de Google no tiene un correo verificado. Verifícalo en Google o regístrate con correo y contraseña.");
        }
        String cleanEmail = email.trim().toLowerCase();
        var existing = userRepository.findByEmailIgnoreCase(cleanEmail);
        if (existing.isPresent()) {
            User user = existing.get();
            if (user.getGoogleId() != null) {
                throw new BusinessRuleException("Este correo ya está vinculado a otra cuenta de Google.");
            }
            user.setGoogleId(googleId);
            return userRepository.save(user);
        }
        int at = cleanEmail.indexOf('@');
        String displayName = !FormText.isBlank(name) ? name.trim() : at > 0 ? cleanEmail.substring(0, at) : cleanEmail;
        return userRepository.save(User.builder()
                .name(displayName)
                .email(cleanEmail)
                .googleId(googleId)
                .role(Role.CLIENT)
                .build());
    }

    /**
     * Crea un usuario. Si {@code rawPassword} es null la cuenta no puede iniciar sesión
     * (clientes dados de alta por un trabajador desde la cartera).
     */
    public User createUser(String name, String email, String phone, String rawPassword, Role role, Center center) {
        return userRepository.save(User.builder()
                .name(name)
                .email(email)
                .phone(phone)
                .password(rawPassword != null ? passwordEncoder.encode(rawPassword) : null)
                .role(role)
                .center(center)
                .build());
    }

    public User updateProfile(Long userId, ProfileForm form) {
        passwordPolicy.validateOptional(form.newPassword(), form.confirmPassword());
        User user = getUser(userId);
        if (!FormText.isBlank(form.name())) {
            user.setName(form.name().trim());
        }
        if (form.phone() != null) {
            user.setPhone(FormText.trimToNull(form.phone()));
        }
        if (!FormText.isBlank(form.newPassword())) {
            user.setPassword(passwordEncoder.encode(form.newPassword()));
            // Al cambiar la contraseña se olvidan los dispositivos con «Recordar mi sesión»
            rememberMeTokens.removeUserTokens(AppUserDetails.usernameOf(user));
        }
        return userRepository.save(user);
    }
}
