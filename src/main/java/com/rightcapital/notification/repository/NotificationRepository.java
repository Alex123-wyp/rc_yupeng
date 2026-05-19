package com.rightcapital.notification.repository;

import com.rightcapital.notification.domain.Notification;
import com.rightcapital.notification.domain.NotificationStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
            NotificationStatus status, OffsetDateTime nextAttemptAt, Pageable pageable);
}
