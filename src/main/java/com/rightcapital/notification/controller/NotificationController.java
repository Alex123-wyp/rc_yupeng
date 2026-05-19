package com.rightcapital.notification.controller;

import com.rightcapital.notification.dto.CreateNotificationRequest;
import com.rightcapital.notification.dto.CreateNotificationResponse;
import com.rightcapital.notification.dto.NotificationResponse;
import com.rightcapital.notification.service.NotificationCommandService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationCommandService notificationCommandService;

    public NotificationController(NotificationCommandService notificationCommandService) {
        this.notificationCommandService = notificationCommandService;
    }

    /**
     * “创建通知任务”API入口函数
     *
     * @param request
     * @return
     */
    @PostMapping
    ResponseEntity<CreateNotificationResponse> create(@Valid @RequestBody CreateNotificationRequest request) {

        // 真正创建通知任务，写入notifications表同时写入notification_outbox表，返回生成的uuid
        UUID id = notificationCommandService.create(request);
        // 返回202，显示已经接受。由于通知是异步完成的，所以很适用于当前场景
        // 客户端可以访问"/api/notifications/" + id进行状态查询
        return ResponseEntity.accepted()
                .location(URI.create("/api/notifications/" + id))
                .body(new CreateNotificationResponse(id));
    }

    @GetMapping("/{id}")
    NotificationResponse get(@PathVariable UUID id) {
        // 把数据库实体转换成NotificationResponse对象
        return NotificationResponse.from(notificationCommandService.get(id));
    }
}
