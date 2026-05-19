package com.rightcapital.notification.dto;

import com.rightcapital.notification.domain.HttpMethodType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashMap;
import java.util.Map;

public record CreateNotificationRequest(
        @NotBlank String targetUrl,
        @NotNull HttpMethodType method,
        Map<String, String> headers,
        String body,
        @Size(max = 255) String idempotencyKey) {

    public Map<String, String> safeHeaders() {
        if (headers == null) {
            return Map.of();
        }
        return new LinkedHashMap<>(headers);
    }
}
