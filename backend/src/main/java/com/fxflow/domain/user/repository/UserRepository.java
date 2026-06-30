package com.fxflow.domain.user.repository;

import com.fxflow.domain.user.entity.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User,Long> {
    Optional<User> findByEmail(String email);

    Optional<User> findByNameAndEmail(String name, String email);

    boolean existsByEmail(String email);
}
