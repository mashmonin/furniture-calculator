package com.example.furniturecalculator.desktop;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.concurrent.CountDownLatch;

import org.junit.jupiter.api.Test;

import javafx.application.Platform;
import javafx.scene.image.Image;

/**
 * Проверяет, что ресурс иконки приложения (задача 3.4) грузится корректно как
 * JavaFX {@link Image} — без самого окна/Application (см. задачу 1.4:
 * визуальная проверка окна в этом окружении недоступна).
 */
class DesktopLauncherIconTest {

	@Test
	void appIconResourceLoadsAsValidImage() throws InterruptedException {
		CountDownLatch toolkitReady = new CountDownLatch(1);
		Platform.startup(toolkitReady::countDown); // инициализирует JavaFX toolkit без показа окна
		toolkitReady.await();

		try (InputStream iconStream = DesktopLauncher.class.getResourceAsStream("/desktop/app-icon.png")) {
			assertThat(iconStream).isNotNull();
			Image icon = new Image(iconStream);
			assertThat(icon.isError()).isFalse();
			assertThat(icon.getWidth()).isPositive();
			assertThat(icon.getHeight()).isPositive();
		} catch (Exception e) {
			throw new AssertionError("Не удалось загрузить иконку приложения", e);
		} finally {
			Platform.exit();
		}
	}

}
