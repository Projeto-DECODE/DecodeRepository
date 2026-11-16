package com.example.sos.auth.service;

import com.example.sos.auth.domain.User;
import com.example.sos.auth.exception.EmailAlreadyInUseException;
import com.example.sos.auth.repository.UserRepository;
import java.time.Clock;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public UserService(UserRepository repository, PasswordEncoder passwordEncoder, Clock clock) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional
    public User register(String name, String email, String rawPassword) {
        String normalizedEmail = normalizeEmail(email);
        if (repository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyInUseException();
        }
        User user = new User(name.trim(), normalizedEmail, passwordEncoder.encode(rawPassword), clock.instant());
        try {
            User saved = repository.saveAndFlush(user);
            log.info("op=register result=success userId={}", saved.getId());
            return saved;
        } catch (DataIntegrityViolationException e) {
            // Cadastros simultâneos com o mesmo e-mail: a restrição UNIQUE do banco é a barreira final.
            throw new EmailAlreadyInUseException();
        }
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
