package com.example.furniturecalculator.desktop;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

import com.example.furniturecalculator.FurnitureCalculatorApplication;

/**
 * Проверяет именно тот способ запуска backend, который использует
 * {@link DesktopLauncher} (SpringApplicationBuilder, профиль desktop,
 * server.port=0 + определение реального порта через WebServerApplicationContext) —
 * без самого JavaFX-окна, которое в этом окружении не отображается физически
 * (см. задачу 1.4 change add-desktop-app-packaging).
 */
class DesktopLauncherBackendStartupTest {

	@Test
	void backendStartsOnRandomPortAndServesUi() throws IOException {
		String originalUserHome = System.getProperty("user.home");
		Path tempHome = Files.createTempDirectory("furniture-calculator-desktop-launcher-test");
		System.setProperty("user.home", tempHome.toString());

		ConfigurableApplicationContext context = null;
		try {
			context = new SpringApplicationBuilder(FurnitureCalculatorApplication.class)
					.profiles("desktop")
					.run("--server.port=0");

			int port = ((WebServerApplicationContext) context).getWebServer().getPort();
			// Не просто > 0 — а именно случайный, отличный от дефолтного 8080 из
			// application.yml. "--server.port=0" передаётся аргументом командной
			// строки, а не через .properties(...) (та имеет более низкий приоритет,
			// чем application.yml, и port=0 тихо не сработал бы).
			assertThat(port).isPositive().isNotEqualTo(8080);

			HttpURLConnection connection = (HttpURLConnection) URI.create("http://localhost:" + port + "/api/door-configurations")
					.toURL()
					.openConnection();
			assertThat(connection.getResponseCode()).isEqualTo(200);
		} finally {
			if (context != null) {
				context.close();
			}
			System.setProperty("user.home", originalUserHome);
		}
	}

}
