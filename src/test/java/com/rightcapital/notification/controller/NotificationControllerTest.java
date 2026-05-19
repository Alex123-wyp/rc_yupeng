package com.rightcapital.notification.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rightcapital.notification.domain.HttpMethodType;
import com.rightcapital.notification.domain.Notification;
import com.rightcapital.notification.dto.CreateNotificationRequest;
import com.rightcapital.notification.service.NotificationCommandService;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(NotificationController.class)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private NotificationCommandService service;

    @Test
    void createsNotificationAndReturnsAccepted() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.create(any(CreateNotificationRequest.class))).thenReturn(id);

        mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "targetUrl", "https://vendor.example/notify",
                                "method", "POST",
                                "headers", Map.of("Content-Type", "application/json"),
                                "body", "{\"event\":\"registered\"}",
                                "idempotencyKey", "registration-1"))))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "/api/notifications/" + id))
                .andExpect(jsonPath("$.notificationId").value(id.toString()));
    }

    @Test
    void getsNotificationStatus() throws Exception {

        UUID id = UUID.randomUUID();
        Notification notification = new Notification(
                id,
                "https://vendor.example/notify",
                HttpMethodType.POST,
                "{}",
                "{}",
                null);
        when(service.get(id)).thenReturn(notification);

        mockMvc.perform(get("/api/notifications/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.attemptCount").value(0));

    }
}
