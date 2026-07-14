package com.unesp.fleetanalyticsapi.app.services;

import com.unesp.fleetanalyticsapi.app.dtos.CreateUserRequest;
import com.unesp.fleetanalyticsapi.app.dtos.UserSummary;

import java.util.UUID;

public interface UserService {

    UserSummary createNewUser(CreateUserRequest request);

    UserSummary findUserByUsername(String username);

    UserSummary disableUser(UUID userId);

}
