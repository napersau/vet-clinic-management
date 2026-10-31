package com.vetclinic.auth.repository;

import com.vetclinic.auth.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // Luôn nạp kèm role: open-in-view đã tắt nên không thể lazy-load sau khi transaction kết thúc
    @EntityGraph(attributePaths = "role")
    Optional<User> findByUsername(String username);

    @EntityGraph(attributePaths = "role")
    Optional<User> findWithRoleById(Long id);

    boolean existsByUsername(String username);

    boolean existsByPhoneNumber(String phoneNumber);
}
