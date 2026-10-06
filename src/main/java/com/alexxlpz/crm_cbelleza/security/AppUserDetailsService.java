package com.alexxlpz.crm_cbelleza.security;

import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Carga usuarios por correo electrónico o, si no existe, por nombre de usuario. */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public AppUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public AppUserDetails loadUserByUsername(String identifier) {
        String clean = identifier == null ? "" : identifier.trim();
        return userRepository.findByEmailIgnoreCase(clean)
                .or(() -> userRepository.findByNameIgnoreCase(clean))
                .map(AppUserDetails::from)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));
    }
}
