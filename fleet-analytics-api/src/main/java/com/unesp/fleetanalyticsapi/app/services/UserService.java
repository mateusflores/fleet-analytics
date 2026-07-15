package com.unesp.fleetanalyticsapi.app.services;

import com.unesp.fleetanalyticsapi.app.dtos.CreateUserRequest;
import com.unesp.fleetanalyticsapi.app.dtos.UserSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface UserService {

    UserSummary createNewUser(CreateUserRequest request);

    Page<UserSummary> findAll(Pageable pageable);

    UserSummary findUserByUsername(String username);

    UserSummary disableUser(UUID userId);

    UserSummary enableUser(UUID userId);
}
