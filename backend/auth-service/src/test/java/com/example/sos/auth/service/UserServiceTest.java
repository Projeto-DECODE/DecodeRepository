package com.example.sos.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.sos.auth.domain.User;
import com.example.sos.auth.exception.EmailAlreadyInUseException;
import com.example.sos.auth.exception.InvalidCredentialsException;
import com.example.sos.auth.repository.UserRepository;
import com.example.sos.auth.security.ServiceClientAuthenticator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    @Mock UserRepository repository;
    @Mock PasswordEncoder encoder;
    @Mock TokenService tokenService;
    @Mock ServiceClientAuthenticator clientAuthenticator;

    @Test
    void registerNormalizesEmailAndStoresOnlyTheHash() {
        UserService service = new UserService(repository, encoder, clock);
        when(repository.existsByEmail("maria@example.com")).thenReturn(false);
        when(encoder.encode("senha-12345")).thenReturn("HASHED");
        when(repository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User user = service.register("  Maria ", " Maria@Example.COM ", "senha-12345");

        assertEquals("maria@example.com", user.getEmail());
        assertEquals("Maria", user.getName());
        assertEquals("HASHED", user.getPasswordHash());
        assertNotEquals("senha-12345", user.getPasswordHash());
        assertEquals(clock.instant(), user.getCreatedAt());
    }

    @Test
    void registerRejectsDuplicateEmail() {
        UserService service = new UserService(repository, encoder, clock);
        when(repository.existsByEmail("maria@example.com")).thenReturn(true);

        assertThrows(EmailAlreadyInUseException.class,
                () -> service.register("Maria", "maria@example.com", "senha-12345"));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void loginWithUnknownEmailStillVerifiesADummyHash() {
        when(encoder.encode(anyString())).thenReturn("DUMMY");
        AuthService service = new AuthService(repository, encoder, tokenService, clientAuthenticator);
        when(repository.findByEmail("x@example.com")).thenReturn(Optional.empty());
        when(encoder.matches("pw", "DUMMY")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> service.login("X@Example.com", "pw"));

        // O custo da verificação de senha é pago mesmo sem usuário, para não vazar existência de conta.
        verify(encoder).matches("pw", "DUMMY");
        verify(tokenService, never()).issueUserToken(any());
    }
}
