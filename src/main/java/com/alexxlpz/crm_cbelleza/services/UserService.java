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
import org.springframework.security.crypto.password.PasswordEncoder;
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

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, PasswordPolicy passwordPolicy) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
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

    /** Alta pública de un cliente. Lanza {@link BusinessRuleException} con un mensaje para el usuario si algo falla. */
    public User registerClient(RegistrationForm form) {
        if (FormText.isBlank(form.name()) || FormText.isBlank(form.email())) {
            throw new BusinessRuleException("Por favor, completa todos los campos requeridos.");
        }
        passwordPolicy.validateRequired(form.password(), form.confirmPassword());
        String email = form.normalizedEmail();
        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new BusinessRuleException("Este correo electrónico ya está registrado. Por favor, inicia sesión.");
        }
        return createUser(form.name().trim(), email, FormText.trimToNull(form.phone()), form.password(), Role.CLIENT, null);
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
        }
        return userRepository.save(user);
    }
}
