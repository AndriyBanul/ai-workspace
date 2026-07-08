package com.aiworkspace.users.repositories;

import com.aiworkspace.users.entities.UserAccountEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserAccountRepository extends JpaRepository<UserAccountEntity, String> {

    Optional<UserAccountEntity> findByEmail(String email);

    boolean existsByEmail(String email);
}
