package com.unesp.fleetanalyticsapi.app.services;

import com.unesp.fleetanalyticsapi.app.dtos.ResetPasswordRequest;
import com.unesp.fleetanalyticsapi.model.entities.Role;
import com.unesp.fleetanalyticsapi.model.entities.User;
import com.unesp.fleetanalyticsapi.model.repositories.RoleRepository;
import com.unesp.fleetanalyticsapi.model.repositories.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserServiceImpl userService;

    private User user;
    private String username;
    private String currentPassword;
    private String newPassword;
    private String encodedPassword;
    private String encodedNewPassword;

    @BeforeEach
    void setUp() {
        username = "testuser";
        currentPassword = "oldpass";
        newPassword = "newpass";
        encodedPassword = "$2a$encodedOld";
        encodedNewPassword = "$2a$encodedNew";

        Role role = new Role();
        role.setName("USER");

        user = User.builder()
                .username(username)
                .password(encodedPassword)
                .role(role)
                .build();
    }

    @Test
    void shouldResetPasswordSuccessfully() {
        ResetPasswordRequest request = new ResetPasswordRequest(
                currentPassword, newPassword, newPassword);

        when(userRepository.findByUsername(username))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches(currentPassword, encodedPassword))
                .thenReturn(true);
        when(passwordEncoder.encode(newPassword))
                .thenReturn(encodedNewPassword);
        when(userRepository.save(any(User.class)))
                .thenReturn(user);

        userService.resetOwnPassword(username, request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertEquals(encodedNewPassword, userCaptor.getValue().getPassword());
    }

    @Test
    void shouldThrowWhenUserNotFound() {
        ResetPasswordRequest request = new ResetPasswordRequest(
                currentPassword, newPassword, newPassword);

        when(userRepository.findByUsername(username))
                .thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> userService.resetOwnPassword(username, request));
    }

    @Test
    void shouldThrowWhenCurrentPasswordIsIncorrect() {
        ResetPasswordRequest request = new ResetPasswordRequest(
                currentPassword, newPassword, newPassword);

        when(userRepository.findByUsername(username))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches(currentPassword, encodedPassword))
                .thenReturn(false);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> userService.resetOwnPassword(username, request));
        assertEquals("Current password is incorrect", exception.getMessage());
    }

    @Test
    void shouldThrowWhenNewPasswordAndConfirmationDoNotMatch() {
        ResetPasswordRequest request = new ResetPasswordRequest(
                currentPassword, newPassword, "differentpass");

        when(userRepository.findByUsername(username))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches(currentPassword, encodedPassword))
                .thenReturn(true);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> userService.resetOwnPassword(username, request));
        assertEquals("New password and confirmation do not match",
                exception.getMessage());
    }
}
