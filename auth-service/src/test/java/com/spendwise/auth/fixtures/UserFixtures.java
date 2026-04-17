package com.spendwise.auth.fixtures;

import com.spendwise.auth.entity.User;

import java.util.UUID;

public final class UserFixtures {

    private UserFixtures() {}

    public static User.UserBuilder aUser() {
        return User.builder()
                .id(UUID.randomUUID())
                .name("João Silva")
                .email("joao@email.com")
                .passwordHash("$2a$10$hashedpassword")
                .role("USER")
                .isActive(true);
    }

    public static User anActiveUser() {
        return aUser().build();
    }

    public static User anInactiveUser() {
        return aUser().isActive(false).build();
    }

    public static User aUserWithEmail(String email) {
        return aUser().email(email).build();
    }
}
