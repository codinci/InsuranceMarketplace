package com.example.insurance.policyservice.policy.messaging;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

  /**
   * Unsent events, oldest first. {@code FOR UPDATE SKIP LOCKED} lets two relays share the table
   * without sending the same row twice (ADR-013). Must run inside a transaction.
   */
  @Query(
      value =
          """
          SELECT * FROM outbox_events
          WHERE published_at IS NULL
          ORDER BY created_at, event_id
          LIMIT :limit
          FOR UPDATE SKIP LOCKED
          """,
      nativeQuery = true)
  List<OutboxEvent> lockUnpublished(@Param("limit") int limit);
}
