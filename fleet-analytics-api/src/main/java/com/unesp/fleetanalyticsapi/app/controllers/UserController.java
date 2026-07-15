package com.unesp.fleetanalyticsapi.app.controllers;

import com.unesp.fleetanalyticsapi.app.dtos.CreateUserRequest;
import com.unesp.fleetanalyticsapi.app.dtos.UserSummary;
import com.unesp.fleetanalyticsapi.app.services.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    @PreAuthorize("hasAuthority('users:create')")
    public ResponseEntity<UserSummary> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserSummary summary = userService.createNewUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(summary);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('users:read')")
    public ResponseEntity<Page<UserSummary>> findAll(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(userService.findAll(pageable));
    }

    @GetMapping("/{username}")
    @PreAuthorize("hasAuthority('users:read')")
    public ResponseEntity<UserSummary> getUserByUsername(@PathVariable String username) {
        UserSummary summary = userService.findUserByUsername(username);
        return ResponseEntity.ok(summary);
    }

    @PatchMapping("/{userId}/disable")
    @PreAuthorize("hasAuthority('users:management')")
    public ResponseEntity<UserSummary> disableUser(@PathVariable UUID userId) {
        UserSummary summary = userService.disableUser(userId);
        return ResponseEntity.ok(summary);
    }

    @PatchMapping("/{userId}/enable")
    @PreAuthorize("hasAuthority('users:management')")
    public ResponseEntity<UserSummary> enableUser(@PathVariable UUID userId) {
        UserSummary summary = userService.enableUser(userId);
        return ResponseEntity.ok(summary);
    }
}
