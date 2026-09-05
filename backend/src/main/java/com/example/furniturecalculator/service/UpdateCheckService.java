package com.example.furniturecalculator.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.example.furniturecalculator.dto.UpdateCheckDto;
import tools.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Пассивная проверка версии при старте (задача 5 change add-desktop-app-packaging).
 * Один HTTP-запрос к статическому JSON {@code {"latestVersion": "...", "downloadUrl": "..."}}
 * в фоновом потоке после старта — ошибка сети/таймаут/некорректный ответ тихо
 * игнорируются, не блокируют и не прерывают работу приложения (см. спецификацию,
 * Requirement: Уведомление о доступном обновлении).
 */
@Slf4j
@Service
public class UpdateCheckService {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String updateCheckUrl;
    private final String currentVersion;
    private final Duration timeout;

    private final AtomicReference<UpdateCheckDto> result = new AtomicReference<>(UpdateCheckDto.notAvailable());

    public UpdateCheckService(ObjectMapper objectMapper, @Value("${app.update-check.url:}") String updateCheckUrl,
            @Value("${app.version:}") String configuredVersion,
            @Value("${app.update-check.timeout-ms:3000}") long timeoutMs) {
        this.objectMapper = objectMapper;
        this.updateCheckUrl = updateCheckUrl;
        this.timeout = Duration.ofMillis(timeoutMs);
        this.httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
        this.currentVersion = configuredVersion.isBlank() ? readImplementationVersion() : configuredVersion;
    }

    private String readImplementationVersion() {
        String version = getClass().getPackage().getImplementationVersion();
        return version != null ? version : "";
    }

    @EventListener(ApplicationReadyEvent.class)
    void onApplicationReady() {
        Thread.ofVirtual().name("update-check").start(this::checkForUpdate);
    }

    void checkForUpdate() {
        if (updateCheckUrl.isBlank() || currentVersion.isBlank()) {
            return;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(updateCheckUrl)).timeout(timeout).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.debug("Проверка обновлений: сервис вернул HTTP {}", response.statusCode());
                return;
            }
            UpdateManifest manifest = objectMapper.readValue(response.body(), UpdateManifest.class);
            if (manifest.latestVersion() != null && isNewer(manifest.latestVersion(), currentVersion)) {
                result.set(new UpdateCheckDto(true, manifest.latestVersion(), manifest.downloadUrl()));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.debug("Проверка обновлений недоступна: {}", e.toString());
        }
    }

    public UpdateCheckDto getResult() {
        return result.get();
    }

    static boolean isNewer(String latest, String current) {
        int[] latestParts = parseVersion(latest);
        int[] currentParts = parseVersion(current);
        if (latestParts == null || currentParts == null) {
            return false;
        }
        int length = Math.max(latestParts.length, currentParts.length);
        for (int i = 0; i < length; i++) {
            int latestPart = i < latestParts.length ? latestParts[i] : 0;
            int currentPart = i < currentParts.length ? currentParts[i] : 0;
            if (latestPart != currentPart) {
                return latestPart > currentPart;
            }
        }
        return false;
    }

    private static int[] parseVersion(String version) {
        try {
            String normalized = version.split("-")[0];
            String[] segments = normalized.split("\\.");
            int[] parts = new int[segments.length];
            for (int i = 0; i < segments.length; i++) {
                parts[i] = Integer.parseInt(segments[i].trim());
            }
            return parts;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private record UpdateManifest(String latestVersion, String downloadUrl) {
    }
}
