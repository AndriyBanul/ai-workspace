package com.aiworkspace.security;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;

@Repository
public interface UserQuotaUsageRepository extends JpaRepository<UserQuotaUsageEntity, String> {

    @Modifying
    @Query("delete from UserQuotaUsageEntity usage where usage.windowStart < :before")
    int deleteExpired(@Param("before") Instant before);
}
