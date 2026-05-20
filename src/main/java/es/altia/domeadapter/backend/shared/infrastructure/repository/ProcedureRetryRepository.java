package es.altia.domeadapter.backend.shared.infrastructure.repository;

import es.altia.domeadapter.backend.shared.domain.model.entities.ProcedureRetry;
import es.altia.domeadapter.backend.shared.domain.model.enums.ActionType;
import es.altia.domeadapter.backend.shared.domain.model.enums.RetryStatus;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface ProcedureRetryRepository extends ReactiveCrudRepository<ProcedureRetry, UUID> {

    Flux<ProcedureRetry> findByStatus(RetryStatus status);

    Mono<ProcedureRetry> findByCredentialIdAndActionType(UUID credentialId, ActionType actionType);

    @Query("SELECT * FROM dome_adapter.procedure_retry WHERE status = 'PENDING' AND first_failure_at < :exhaustionThreshold")
    Flux<ProcedureRetry> findPendingRecordsOlderThan(Instant exhaustionThreshold);

    @Modifying
    @Query("""
            INSERT INTO dome_adapter.procedure_retry
                (id, credential_id, action_type, status, attempt_count, first_failure_at, payload, last_error, issued_by)
            VALUES
                (:#{#retry.id}, :#{#retry.credentialId}, :#{#retry.actionType}, :#{#retry.status},
                 :#{#retry.attemptCount}, :#{#retry.firstFailureAt}, :#{#retry.payload}, :#{#retry.lastError}, :#{#retry.issuedBy})
            ON CONFLICT (credential_id, action_type)
            DO UPDATE SET
                status = EXCLUDED.status,
                payload = EXCLUDED.payload,
                last_error = EXCLUDED.last_error
            """)
    Mono<Integer> upsert(ProcedureRetry retry);

    @Modifying
    @Query("""
            UPDATE dome_adapter.procedure_retry
            SET attempt_count = attempt_count + 1,
                last_attempt_at = :lastAttemptAt,
                last_error = :lastError
            WHERE credential_id = :credentialId AND action_type = :actionType
            """)
    Mono<Integer> incrementAttemptCount(UUID credentialId, ActionType actionType, Instant lastAttemptAt, String lastError);

    @Modifying
    @Query("""
            UPDATE dome_adapter.procedure_retry
            SET status = 'COMPLETED', last_attempt_at = now()
            WHERE credential_id = :credentialId AND action_type = :actionType
            """)
    Mono<Integer> markAsCompleted(UUID credentialId, ActionType actionType);

    @Modifying
    @Query("""
            UPDATE dome_adapter.procedure_retry
            SET status = 'RETRY_EXHAUSTED'
            WHERE credential_id = :credentialId AND action_type = :actionType
            """)
    Mono<Integer> markAsExhausted(UUID credentialId, ActionType actionType);
}
