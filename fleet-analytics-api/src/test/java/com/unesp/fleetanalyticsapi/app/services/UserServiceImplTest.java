package com.unesp.fleetanalyticsapi.app.services;

import com.unesp.fleetanalyticsapi.app.dtos.CreateUserRequest;
import com.unesp.fleetanalyticsapi.app.dtos.ResetPasswordRequest;
import com.unesp.fleetanalyticsapi.app.dtos.UpdateUserRequest;
import com.unesp.fleetanalyticsapi.app.dtos.UserSummary;
import com.unesp.fleetanalyticsapi.model.entities.crud.Role;
import com.unesp.fleetanalyticsapi.model.entities.crud.User;
import com.unesp.fleetanalyticsapi.model.repositories.RoleRepository;
import com.unesp.fleetanalyticsapi.model.repositories.UserRepository;
import jakarta.persistence.EntityExistsException;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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

    // ---------------------------------------------------------------------------------------------
    // cadastro

    private CreateUserRequest createRequest() {
        return new CreateUserRequest("joao.silva", "joao@fleet.com", "secret1", UUID.randomUUID(),
                "João", "Silva", "11999990000");
    }

    @Test
    void createNewUserEncodesPasswordAndPersistsAllFields() {
        CreateUserRequest request = createRequest();
        Role driver = Role.builder().id(request.roleId()).name("Driver").build();
        when(userRepository.findByUsername("joao.silva")).thenReturn(Optional.empty());
        when(userRepository.existsByEmail("joao@fleet.com")).thenReturn(false);
        when(roleRepository.findById(request.roleId())).thenReturn(Optional.of(driver));
        when(passwordEncoder.encode("secret1")).thenReturn("$2a$hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserSummary summary = userService.createNewUser(request);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getPassword()).isEqualTo("$2a$hash");
        assertThat(saved.getValue().getEnabled()).isTrue();
        assertThat(saved.getValue().getRole()).isSameAs(driver);
        assertThat(summary.username()).isEqualTo("joao.silva");
        assertThat(summary.roleName()).isEqualTo("Driver");
        assertThat(summary.phone()).isEqualTo("11999990000");
    }

    @Test
    void createNewUserRejectsDuplicateUsername() {
        when(userRepository.findByUsername("joao.silva")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.createNewUser(createRequest()))
                .isInstanceOf(EntityExistsException.class).hasMessageContaining("Username");
        verify(userRepository, never()).save(any());
    }

    @Test
    void createNewUserRejectsDuplicateEmail() {
        when(userRepository.findByUsername("joao.silva")).thenReturn(Optional.empty());
        when(userRepository.existsByEmail("joao@fleet.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.createNewUser(createRequest()))
                .isInstanceOf(EntityExistsException.class).hasMessageContaining("Email");
        verify(userRepository, never()).save(any());
    }

    @Test
    void createNewUserRejectsUnknownRole() {
        CreateUserRequest request = createRequest();
        when(userRepository.findByUsername("joao.silva")).thenReturn(Optional.empty());
        when(roleRepository.findById(request.roleId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.createNewUser(request)).isInstanceOf(EntityNotFoundException.class);
        verify(userRepository, never()).save(any());
    }

    // ---------------------------------------------------------------------------------------------
    // edição

    private User existing(UUID id) {
        Role role = Role.builder().name("Driver").build();
        return User.builder().id(id).username("joao.silva").email("joao@fleet.com").firstName("João")
                .lastName("Silva").phone("1").enabled(true).role(role).build();
    }

    @Test
    void updateUserChangesOnlyProvidedFields() {
        UUID id = UUID.randomUUID();
        User current = existing(id);
        when(userRepository.findById(id)).thenReturn(Optional.of(current));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserSummary summary = userService.updateUser(id, new UpdateUserRequest(null, "Souza", "22", null));

        assertThat(summary.firstName()).isEqualTo("João");
        assertThat(summary.lastName()).isEqualTo("Souza");
        assertThat(summary.phone()).isEqualTo("22");
        assertThat(summary.email()).isEqualTo("joao@fleet.com");
        verify(userRepository, never()).existsByEmailAndIdNot(any(), any());
    }

    @Test
    void updateUserAllowsKeepingOwnEmailAndChangingToFreeOne() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.of(existing(id)));
        when(userRepository.existsByEmailAndIdNot("novo@fleet.com", id)).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(userService.updateUser(id, new UpdateUserRequest(null, null, null, "novo@fleet.com")).email())
                .isEqualTo("novo@fleet.com");
    }

    @Test
    void updateUserRejectsEmailOfAnotherUser() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.of(existing(id)));
        when(userRepository.existsByEmailAndIdNot("outro@fleet.com", id)).thenReturn(true);

        assertThatThrownBy(() -> userService.updateUser(id, new UpdateUserRequest(null, null, null, "outro@fleet.com")))
                .isInstanceOf(EntityExistsException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateUserFailsForUnknownId() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateUser(id, new UpdateUserRequest("A", null, null, null)))
                .isInstanceOf(EntityNotFoundException.class);
    }
}
