package com.example.sos.auth.service;

import com.example.sos.auth.domain.User;
import com.example.sos.auth.exception.InvalidClientException;
import com.example.sos.auth.exception.InvalidCredentialsException;
import com.example.sos.auth.repository.UserRepository;
import com.example.sos.auth.security.ServiceClientAuthenticator;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final ServiceClientAuthenticator clientAuthenticator;
    /** Hash fictício verificado quando o e-mail não existe, para igualar o tempo de resposta. */
    private final String dummyHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
            TokenService tokenService, ServiceClientAuthenticator clientAuthenticator) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.clientAuthenticator = clientAuthenticator;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    public IssuedToken login(String email, String password) {
        Optional<User> user = userRepository.findByEmail(UserService.normalizeEmail(email));
        String hashToCheck = user.map(User::getPasswordHash).orElse(dummyHash);
        boolean passwordMatches = passwordEncoder.matches(password, hashToCheck);
        if (user.isEmpty() || !passwordMatches) {
            log.warn("op=login result=rejected");
            throw new InvalidCredentialsException();
        }
        log.info("op=login result=success userId={}", user.get().getId());
        return tokenService.issueUserToken(user.get().getId());
    }

    public IssuedToken issueServiceToken(String clientId, String clientSecret) {
        if (!clientAuthenticator.authenticate(clientId, clientSecret)) {
            log.warn("op=service-token result=rejected");
            throw new InvalidClientException();
        }
        log.info("op=service-token result=success clientId={}", clientId);
        return tokenService.issueServiceToken(clientId);
    }
}
