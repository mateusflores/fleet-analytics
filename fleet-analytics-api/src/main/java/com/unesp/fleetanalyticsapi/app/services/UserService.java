package com.unesp.fleetanalyticsapi.app.services;

import com.unesp.fleetanalyticsapi.app.dtos.CreateUserRequest;
import com.unesp.fleetanalyticsapi.app.dtos.ResetPasswordRequest;
import com.unesp.fleetanalyticsapi.app.dtos.UpdateUserRequest;
import com.unesp.fleetanalyticsapi.app.dtos.UserSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface UserService {

    UserSummary createNewUser(CreateUserRequest request);

    Page<UserSummary> findAll(Pageable pageable);

    Page<UserSummary> searchUsers(String firstName, String lastName, String email,
                                  String username, String roleName, Pageable pageable);

    UserSummary findUserByUsername(String username);

    UserSummary disableUser(UUID userId);

    UserSummary enableUser(UUID userId);

    void resetOwnPassword(String username, ResetPasswordRequest request);

    UserSummary updateUser(UUID userId, UpdateUserRequest request);
}
