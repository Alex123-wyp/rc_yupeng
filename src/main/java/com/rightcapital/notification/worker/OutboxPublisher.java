package com.rightcapital.notification.worker;

import com.rightcapital.notification.config.NotificationProperties;
import com.rightcapital.notification.domain.NotificationOutbox;
import com.rightcapital.notification.domain.OutboxStatus;
import com.rightcapital.notification.repository.NotificationOutboxRepository;
import com.rightcapital.notification.service.NotificationMessage;
import java.util.List;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OutboxPublisher {

    private final NotificationProperties properties;
    private final NotificationOutboxRepository outboxRepository;
    private final RabbitTemplate rabbitTemplate;

    public OutboxPublisher(NotificationProperties properties, NotificationOutboxRepository outboxRepository,
            RabbitTemplate rabbitTemplate) {
        this.properties = properties;
        this.outboxRepository = outboxRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    //在Springboot启动后每隔2000ms自动执行一次publishPendingOutboxRows（）
    @Scheduled(fixedDelayString = "${notification.delivery.outbox-publisher-delay:2000}")
    @Transactional
    public void publishPendingOutboxRows() {
        List<NotificationOutbox> rows = outboxRepository.findByStatusInOrderByCreatedAtAsc(
                //检查outbox状态是PENDING或者FAILED的记录
                List.of(OutboxStatus.PENDING, OutboxStatus.FAILED),
                PageRequest.of(0, properties.delivery().outboxBatchSize()));

        for (NotificationOutbox row : rows) {
            try {
                //rabbitmq发送通知内容
                rabbitTemplate.convertAndSend(
                        properties.rabbitmq().exchange(),
                        properties.rabbitmq().routingKey(),
                        new NotificationMessage(row.getNotificationId()));
                row.markPublished();
            } catch (RuntimeException exception) {
                row.markPublishFailed(exception.getMessage());
            }
        }
    }
}
