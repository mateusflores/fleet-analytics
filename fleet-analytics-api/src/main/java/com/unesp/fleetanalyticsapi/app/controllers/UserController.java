package com.unesp.fleetanalyticsapi.app.controllers;

import com.unesp.fleetanalyticsapi.app.dtos.CreateUserRequest;
import com.unesp.fleetanalyticsapi.app.dtos.UserSummary;
import com.unesp.fleetanalyticsapi.app.services.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    public ResponseEntity<UserSummary> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserSummary summary = userService.createNewUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(summary);
    }

    @GetMapping("/{username}")
    public ResponseEntity<UserSummary> getUserByUsername(@PathVariable String username) {
        UserSummary summary = userService.findUserByUsername(username);
        return ResponseEntity.ok(summary);
    }

    @PatchMapping("/{userId}/disable")
    public ResponseEntity<UserSummary> disableUser(@PathVariable UUID userId) {
        UserSummary summary = userService.disableUser(userId);
        return ResponseEntity.ok(summary);
    }
}
