package com.example.furniturecalculator.desktop;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.web.WebView;
import javafx.stage.Screen;
import javafx.stage.Stage;

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

	private static final String WINDOW_TITLE = "Furniture Calculator";
	private static final String APP_ICON_RESOURCE = "/desktop/app-icon.png";

	private ConfigurableApplicationContext backendContext;

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

}
