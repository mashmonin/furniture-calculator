package com.example.furniturecalculator.desktop;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;

import javafx.application.Application;
import javafx.concurrent.Worker;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Screen;
import javafx.stage.Stage;

import netscape.javascript.JSObject;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

import com.example.furniturecalculator.FurnitureCalculatorApplication;

/**
 * Точка входа для desktop-дистрибутива: поднимает backend (профиль
 * {@code desktop}, файловая H2) в фоновом потоке и открывает UI в нативном
 * окне JavaFX {@link WebView} вместо системного браузера.
 */
public class DesktopLauncher extends Application {

	private static final String WINDOW_TITLE = "Я-Конфигуратор";
	private static final String APP_ICON_RESOURCE = "/desktop/app-icon.png";

	private ConfigurableApplicationContext backendContext;
	// Сильная ссылка обязательна: JavaFX держит JS-мост слабой ссылкой, иначе GC его соберёт.
	private final FileSaveBridge fileSaveBridge = new FileSaveBridge();

	public static void main(String[] args) {
		launch(args);
	}

	@Override
	public void start(Stage stage) {
		WebView webView = new WebView();

		Rectangle2D visualBounds = Screen.getPrimary().getVisualBounds();
		Scene scene = new Scene(webView, Math.min(1280, visualBounds.getWidth()), Math.min(860, visualBounds.getHeight()));

		stage.setTitle(WINDOW_TITLE);
		stage.getIcons().add(new Image(DesktopLauncher.class.getResourceAsStream(APP_ICON_RESOURCE)));
		stage.setScene(scene);
		stage.show();

		// WebView не умеет скачивать файлы (a[download] игнорируется), поэтому после каждой загрузки
		// страницы публикуем в JS мост window.desktopBridge с диалогом «Сохранить как».
		fileSaveBridge.setStage(stage);
		webView.getEngine().getLoadWorker().stateProperty().addListener((observable, oldState, newState) -> {
			if (newState == Worker.State.SUCCEEDED) {
				JSObject window = (JSObject) webView.getEngine().executeScript("window");
				window.setMember("desktopBridge", fileSaveBridge);
			}
		});

		Thread backendThread = new Thread(() -> startBackendAndLoadUi(webView), "backend-startup");
		backendThread.setDaemon(true);
		backendThread.start();
	}

	private void startBackendAndLoadUi(WebView webView) {
		ConfigurableApplicationContext context = new SpringApplicationBuilder(FurnitureCalculatorApplication.class)
				.profiles("desktop")
				.run("--server.port=0");
		this.backendContext = context;

		int port = ((WebServerApplicationContext) context).getWebServer().getPort();
		Platform.runLater(() -> webView.getEngine().load("http://localhost:" + port + "/"));
	}

	@Override
	public void stop() {
		if (backendContext != null) {
			backendContext.close();
		}
		System.exit(0);
	}

	/**
	 * Мост для JS: сохранение файла (в base64) через нативное окно «Сохранить как».
	 * Класс и методы должны быть public — иначе WebView не увидит их из JavaScript.
	 */
	public static class FileSaveBridge {

		private Stage stage;

		void setStage(Stage stage) {
			this.stage = stage;
		}

		/** @return true, если файл сохранён; false, если пользователь отменил диалог */
		public boolean saveFile(String filename, String base64Content) throws IOException {
			FileChooser chooser = new FileChooser();
			chooser.setTitle("Сохранить файл");
			chooser.setInitialFileName(filename);
			File downloads = new File(System.getProperty("user.home"), "Downloads");
			if (downloads.isDirectory()) {
				chooser.setInitialDirectory(downloads);
			}
			File target = chooser.showSaveDialog(stage);
			if (target == null) {
				return false;
			}
			Files.write(target.toPath(), Base64.getDecoder().decode(base64Content));
			return true;
		}

	}

}
