package es.altia.domeadapter.backend.shared.domain.model.entities;

import lombok.*;
import es.altia.domeadapter.backend.shared.domain.model.enums.ActionType;
import es.altia.domeadapter.backend.shared.domain.model.enums.RetryStatus;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
@Table("dome_adapter.procedure_retry")
public class ProcedureRetry {

    @Id
    @Column("id")
    private UUID id;

    @Column("credential_id")
    private UUID credentialId;

    @Column("action_type")
    private ActionType actionType;

    @Column("status")
    private RetryStatus status;

    @Column("attempt_count")
    private Integer attemptCount;

    @Column("last_attempt_at")
    private Instant lastAttemptAt;

    @Column("first_failure_at")
    private Instant firstFailureAt;

    @Column("payload")
    private String payload;

    @Column("last_error")
    private String lastError;

    @Column("issued_by")
    private String issuedBy;
}
