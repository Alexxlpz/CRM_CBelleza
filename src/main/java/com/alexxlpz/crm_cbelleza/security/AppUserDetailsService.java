package com.alexxlpz.crm_cbelleza.security;

import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Carga usuarios por correo electrónico o, si no existe, por nombre de usuario. El nombre no es único (cualquiera
 * puede registrarse con el de otra persona): solo vale si lo tiene una única cuenta; si no, hay que entrar con el correo.
 */
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
                .or(() -> uniqueName(clean))
                .map(AppUserDetails::from)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));
    }

    private Optional<User> uniqueName(String name) {
        List<User> matches = userRepository.findTop2ByNameIgnoreCase(name);
        return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
    }
}
