package com.ptiters.marketplace.security;

import com.ptiters.marketplace.user.UserRole;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AuthenticatedUser {

    private Long userId;
    private String email;
    private UserRole role;
}