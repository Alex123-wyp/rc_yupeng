package com.rightcapital.notification.worker;

import com.rightcapital.notification.domain.Notification;
import com.rightcapital.notification.domain.NotificationOutbox;
import com.rightcapital.notification.domain.NotificationStatus;
import com.rightcapital.notification.repository.NotificationOutboxRepository;
import com.rightcapital.notification.repository.NotificationRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RetryScheduler {

    private final NotificationRepository notificationRepository;
    private final NotificationOutboxRepository outboxRepository;

    public RetryScheduler(NotificationRepository notificationRepository, NotificationOutboxRepository outboxRepository) {
        this.notificationRepository = notificationRepository;
        this.outboxRepository = outboxRepository;
    }

    @Scheduled(fixedDelayString = "${notification.delivery.retry-scheduler-delay:5000}")
    @Transactional
    //轮询已经过期的任务，并且将这些已经过期的任务状态重新设置为PENDING，以便于再次被publisher检测到并发送
    public void enqueueDueRetries() {
        List<Notification> dueNotifications =
                notificationRepository.findByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
                        NotificationStatus.RETRY_SCHEDULED,
                        OffsetDateTime.now(),
                        PageRequest.of(0, 50));

        for (Notification notification : dueNotifications) {
            notification.markPendingForRetry();
            outboxRepository.save(new NotificationOutbox(UUID.randomUUID(), notification.getId()));
        }
    }
}
