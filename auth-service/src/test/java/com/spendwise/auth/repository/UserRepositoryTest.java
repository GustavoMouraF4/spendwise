package com.spendwise.auth.repository;

import com.spendwise.auth.entity.User;
import com.spendwise.auth.fixtures.UserFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = Replace.NONE)
@DisplayName("UserRepository — testes de slice JPA")
class UserRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine").withReuse(true);

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("Deve encontrar usuário pelo e-mail quando ele existir")
    void shouldFindByEmail_whenUserExists() {
        // Arrange
        User user = UserFixtures.aUser().id(null).build();
        userRepository.save(user);

        // Act
        Optional<User> result = userRepository.findByEmail(user.getEmail());

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo(user.getEmail());
        assertThat(result.get().getName()).isEqualTo(user.getName());
    }

    @Test
    @DisplayName("Deve retornar Optional vazio quando e-mail não existir")
    void shouldReturnEmpty_whenEmailNotFound() {
        // Act
        Optional<User> result = userRepository.findByEmail("naoexiste@email.com");

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Deve retornar true quando e-mail já estiver cadastrado")
    void shouldReturnTrue_existsByEmail_whenUserExists() {
        // Arrange
        User user = UserFixtures.aUser().id(null).build();
        userRepository.save(user);

        // Act
        boolean exists = userRepository.existsByEmail(user.getEmail());

        // Assert
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("Deve retornar false quando e-mail não estiver cadastrado")
    void shouldReturnFalse_existsByEmail_whenUserAbsent() {
        // Act
        boolean exists = userRepository.existsByEmail("novo@email.com");

        // Assert
        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("Deve lançar DataIntegrityViolationException quando e-mail for duplicado (RN-AUTH-01)")
    void shouldThrowConstraintViolation_whenEmailIsDuplicate() {
        // Arrange
        User first = UserFixtures.aUser().id(null).email("dup@email.com").build();
        User second = UserFixtures.aUser().id(null).email("dup@email.com").build();
        userRepository.saveAndFlush(first);

        // Act + Assert
        assertThatThrownBy(() -> userRepository.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
