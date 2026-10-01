package com.alexxlpz.crm_cbelleza.services;

import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.entities.Role;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<User> getWorkersByCenter(Long centerId) {
        return userRepository.findByCenterIdAndRole(centerId, Role.WORKER);
    }

    public Optional<User> findById(Long id) {
        return id != null ? userRepository.findById(id) : Optional.empty();
    }

    public User getUserById(Long id) {
        return id != null ? userRepository.findById(id).orElse(null) : null;
    }

    public Optional<User> authenticate(String identifier, String rawPassword) {
        if (identifier == null || rawPassword == null) {
            return Optional.empty();
        }
        String cleanIdentifier = identifier.trim();
        Optional<User> userOpt = userRepository.findByEmailIgnoreCase(cleanIdentifier);
        if (userOpt.isEmpty()) {
            userOpt = userRepository.findByNameIgnoreCase(cleanIdentifier);
        }
        return userOpt.filter(user -> user.getPassword() != null && passwordEncoder.matches(rawPassword, user.getPassword()));
    }

    public Optional<User> findByEmail(String email) {
        if (email == null) return Optional.empty();
        return userRepository.findByEmailIgnoreCase(email.trim());
    }

    public User registerUser(String name, String email, String phone, String rawPassword, Role role, Center center) {
        String encodedPassword = rawPassword != null ? passwordEncoder.encode(rawPassword) : null;
        User user = User.builder()
                .name(name)
                .email(email)
                .phone(phone)
                .password(encodedPassword)
                .role(role)
                .center(center)
                .build();
        return userRepository.save(user);
    }

    public User updateUserProfile(Long userId, String name, String phone, String newPassword) {
        User user = getUserById(userId);
        if (user != null) {
            if (name != null && !name.trim().isEmpty()) {
                user.setName(name.trim());
            }
            if (phone != null) {
                user.setPhone(phone.trim().isEmpty() ? null : phone.trim());
            }
            if (newPassword != null && !newPassword.trim().isEmpty()) {
                user.setPassword(passwordEncoder.encode(newPassword.trim()));
            }
            return userRepository.save(user);
        }
        return null;
    }
}
