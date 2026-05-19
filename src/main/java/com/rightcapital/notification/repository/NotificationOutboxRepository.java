package com.rightcapital.notification.repository;

import com.rightcapital.notification.domain.NotificationOutbox;
import com.rightcapital.notification.domain.OutboxStatus;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, UUID> {

    List<NotificationOutbox> findByStatusInOrderByCreatedAtAsc(Collection<OutboxStatus> statuses, Pageable pageable);
}
