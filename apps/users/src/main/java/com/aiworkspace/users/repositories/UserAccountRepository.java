package com.aiworkspace.users.repositories;

import com.aiworkspace.users.entities.UserAccountEntity;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserAccountRepository extends JpaRepository<UserAccountEntity, String> {

    Optional<UserAccountEntity> findByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from UserAccountEntity account where account.email = :email")
    Optional<UserAccountEntity> lockByEmail(@Param("email") String email);

    boolean existsByEmail(String email);
}
