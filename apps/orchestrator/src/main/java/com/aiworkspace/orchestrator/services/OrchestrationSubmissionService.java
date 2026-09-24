package com.aiworkspace.orchestrator.services;

import com.aiworkspace.orchestrator.entities.OrchestrationSubmissionRequestEntity;
import com.aiworkspace.orchestrator.models.OrchestrationContent;
import com.aiworkspace.orchestrator.models.OrchestrationSubmission;
import com.aiworkspace.orchestrator.repositories.OrchestrationSubmissionRequestRepository;
import com.aiworkspace.workspaces.exceptions.WorkspaceSourceConflictException;
import com.aiworkspace.workspaces.services.WorkspaceService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Arrays;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;

/** Atomically reserves request keys, persists responses, and dispatches workers after commit. */
@Service
public class OrchestrationSubmissionService {

    private final OrchestratorService orchestrator;
    private final WorkspaceService workspaces;
    private final OrchestrationSubmissionRequestRepository requests;
    private final ObjectMapper json;

    public OrchestrationSubmissionService(OrchestratorService orchestrator, WorkspaceService workspaces,
            OrchestrationSubmissionRequestRepository requests, ObjectMapper json) {
        this.orchestrator = orchestrator;
        this.workspaces = workspaces;
        this.requests = requests;
        this.json = json;
    }

    @Transactional
    public OrchestrationSubmission submitUploads(String ownerId, String workspaceId, String idempotencyKey,
            OrchestrationContent document, OrchestrationContent audio, OrchestrationContent image,
            OrchestrationContent video) throws IOException {
        String ownedWorkspaceId = workspaces.getWorkspace(ownerId, workspaceId).id();
        String fingerprint = fingerprint("upload", Arrays.asList(document, audio, image, video));
        Reservation reservation = reserve(ownerId, ownedWorkspaceId, idempotencyKey, fingerprint);
        if (reservation.replayed() != null) return reservation.replayed();
        OrchestrationSubmission submission = orchestrator.process(ownerId, ownedWorkspaceId,
                document, audio, image, video);
        complete(reservation.entity(), submission);
        return submission;
    }

    @Transactional
    public OrchestrationSubmission reprocess(String ownerId, String workspaceId, String sourceId,
            String idempotencyKey) throws IOException {
        var source = workspaces.getFile(ownerId, workspaceId, sourceId);
        String fingerprint = fingerprint("reprocess", source.id());
        Reservation reservation = reserve(ownerId, source.workspaceId(), idempotencyKey, fingerprint);
        if (reservation.replayed() != null) return reservation.replayed();
        OrchestrationSubmission submission = orchestrator.reprocessSource(ownerId, source.workspaceId(), source.id());
        complete(reservation.entity(), submission);
        return submission;
    }

    @Scheduled(cron = "0 10 3 * * *")
    @Transactional
    public void pruneExpiredIdempotencyKeys() {
        requests.deleteExpired(Instant.now().minus(30, ChronoUnit.DAYS));
    }

    private Reservation reserve(String ownerId, String workspaceId, String key, String fingerprint)
            throws IOException {
        if (key == null || key.isBlank()) return new Reservation(null, null);
        String normalized = key.trim();
        if (normalized.length() > 128 || normalized.chars().anyMatch(character -> character < 33 || character > 126)) {
            throw new IllegalArgumentException("Idempotency-Key must contain 1-128 visible ASCII characters");
        }
        String id = fingerprint("key", ownerId + "\u0000" + normalized);
        var existing = requests.findById(id);
        if (existing.isPresent()) {
            var request = existing.get();
            if (!workspaceId.equals(request.getWorkspaceId()) || !fingerprint.equals(request.getFingerprint())) {
                throw new WorkspaceSourceConflictException("Idempotency-Key was used for a different request");
            }
            if (request.getResponseJson() == null) {
                throw new WorkspaceSourceConflictException("Idempotent submission is still pending; retry shortly");
            }
            return new Reservation(null, json.readValue(request.getResponseJson(), OrchestrationSubmission.class));
        }
        try {
            return new Reservation(requests.saveAndFlush(new OrchestrationSubmissionRequestEntity(
                    id, ownerId, workspaceId, fingerprint, Instant.now())), null);
        } catch (DataIntegrityViolationException exception) {
            throw new WorkspaceSourceConflictException("Idempotent submission is being committed; retry shortly");
        }
    }

    private void complete(OrchestrationSubmissionRequestEntity entity, OrchestrationSubmission submission)
            throws JsonProcessingException {
        if (entity == null) return;
        entity.complete(json.writeValueAsString(submission));
        requests.save(entity);
    }

    private String fingerprint(String operation, List<OrchestrationContent> parts) {
        MessageDigest digest = sha256();
        update(digest, operation);
        for (int index = 0; index < parts.size(); index++) {
            OrchestrationContent part = parts.get(index);
            update(digest, Integer.toString(index));
            update(digest, part == null ? "absent" : "present");
            if (part != null) {
                update(digest, part.filename());
                update(digest, part.contentType());
                byte[] bytes = part.content() == null ? new byte[0] : part.content();
                digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
                digest.update(bytes);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private String fingerprint(String operation, String value) {
        MessageDigest digest = sha256();
        update(digest, operation);
        update(digest, value);
        return HexFormat.of().formatHex(digest.digest());
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void update(MessageDigest digest, String value) {
        if (value == null) {
            digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(-1).array());
            return;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private record Reservation(OrchestrationSubmissionRequestEntity entity, OrchestrationSubmission replayed) {
    }
}
