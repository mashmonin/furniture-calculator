package com.example.furniturecalculator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.example.furniturecalculator.dto.UpdateCheckDto;
import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.json.JsonMapper;

/**
 * Три сценария из спецификации (Requirement: Уведомление о доступном обновлении,
 * задача 5.4): новая версия доступна, установлена актуальная версия, проверка
 * недоступна. Локальный HTTP-сервер вместо реального внешнего URL.
 */
class UpdateCheckServiceTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private String startServer(int statusCode, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/update.json", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusCode, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return "http://localhost:" + server.getAddress().getPort() + "/update.json";
    }

    @Test
    void newerVersionAvailableIsReported() throws IOException {
        String url = startServer(200, "{\"latestVersion\":\"1.1.0\",\"downloadUrl\":\"https://example.com/download\"}");
        UpdateCheckService service = new UpdateCheckService(JsonMapper.builder().build(), url, "1.0.0", 2000);

        service.checkForUpdate();

        UpdateCheckDto result = service.getResult();
        assertThat(result.updateAvailable()).isTrue();
        assertThat(result.latestVersion()).isEqualTo("1.1.0");
        assertThat(result.downloadUrl()).isEqualTo("https://example.com/download");
    }

    @Test
    void currentVersionIsUpToDateNoNotification() throws IOException {
        String url = startServer(200, "{\"latestVersion\":\"1.0.0\",\"downloadUrl\":\"https://example.com/download\"}");
        UpdateCheckService service = new UpdateCheckService(JsonMapper.builder().build(), url, "1.0.0", 2000);

        service.checkForUpdate();

        assertThat(service.getResult()).isEqualTo(UpdateCheckDto.notAvailable());
    }

    @Test
    void unreachableServiceDoesNotThrowAndStartsNormally() {
        // Порт, на котором заведомо никто не слушает.
        UpdateCheckService service = new UpdateCheckService(JsonMapper.builder().build(), "http://localhost:1/update.json",
                "1.0.0", 500);

        assertThatCode(service::checkForUpdate).doesNotThrowAnyException();
        assertThat(service.getResult()).isEqualTo(UpdateCheckDto.notAvailable());
    }

    @Test
    void malformedResponseDoesNotThrow() throws IOException {
        String url = startServer(200, "not a json");
        UpdateCheckService service = new UpdateCheckService(JsonMapper.builder().build(), url, "1.0.0", 2000);

        assertThatCode(service::checkForUpdate).doesNotThrowAnyException();
        assertThat(service.getResult()).isEqualTo(UpdateCheckDto.notAvailable());
    }

    @Test
    void blankUrlSkipsCheckWithoutError() {
        UpdateCheckService service = new UpdateCheckService(JsonMapper.builder().build(), "", "1.0.0", 2000);

        assertThatCode(service::checkForUpdate).doesNotThrowAnyException();
        assertThat(service.getResult()).isEqualTo(UpdateCheckDto.notAvailable());
    }

    @Test
    void isNewerComparesVersionsNumerically() {
        assertThat(UpdateCheckService.isNewer("1.2.0", "1.1.9")).isTrue();
        assertThat(UpdateCheckService.isNewer("1.1.9", "1.2.0")).isFalse();
        assertThat(UpdateCheckService.isNewer("1.0.0", "1.0.0")).isFalse();
        assertThat(UpdateCheckService.isNewer("1.2", "1.2.0")).isFalse();
        assertThat(UpdateCheckService.isNewer("1.2.1", "1.2")).isTrue();
        assertThat(UpdateCheckService.isNewer("1.0.0-beta", "0.9.0")).isTrue();
        assertThat(UpdateCheckService.isNewer("garbage", "1.0.0")).isFalse();
    }
}
