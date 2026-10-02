package com.aiworkspace.security;

import com.aiworkspace.config.SecurityProperties;
import com.aiworkspace.users.services.UserAccountService;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;

/** Serializes each user's quota updates on the account row, across API instances. */
@Service
public class UserQuotaService {

    private final UserAccountService users;
    private final UserQuotaUsageRepository usage;
    private final SecurityProperties properties;

    public UserQuotaService(UserAccountService users, UserQuotaUsageRepository usage,
            SecurityProperties properties) {
        this.users = users;
        this.usage = usage;
        this.properties = properties;
    }

    @Transactional
    public QuotaDecision acquire(String email, Action action) {
        String userId = users.lockUserIdByEmail(email);
        Instant now = Instant.now();
        Instant start = now.truncatedTo(ChronoUnit.DAYS);
        String id = userId + ':' + action.name() + ':' + start.toEpochMilli();
        UserQuotaUsageEntity current = usage.findById(id).orElseGet(() ->
                new UserQuotaUsageEntity(id, userId, action.name(), start));
        int limit = switch (action) {
            case INGESTION -> properties.userQuota().ingestionsPerDay();
            case ANSWER -> properties.userQuota().answersPerDay();
            case GENERATION -> properties.userQuota().generationsPerDay();
        };
        if (current.getConsumed() >= limit) {
            long retryAfter = Math.max(1, Duration.between(now, start.plus(1, ChronoUnit.DAYS)).toSeconds());
            return new QuotaDecision(false, retryAfter);
        }
        current.consume();
        usage.save(current);
        return new QuotaDecision(true, 0);
    }

    @Scheduled(cron = "0 20 3 * * *")
    @Transactional
    public void pruneExpiredUsage() {
        usage.deleteExpired(Instant.now().minus(35, ChronoUnit.DAYS));
    }

    public enum Action {
        INGESTION, ANSWER, GENERATION
    }

    public record QuotaDecision(boolean allowed, long retryAfterSeconds) {
    }
}
