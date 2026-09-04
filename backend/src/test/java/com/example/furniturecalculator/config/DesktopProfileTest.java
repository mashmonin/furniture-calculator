package com.example.furniturecalculator.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.example.furniturecalculator.repository.DoorConfigurationRepository;

/**
 * Профиль desktop (задачи 2.1/2.2 change add-desktop-app-packaging): файловая H2
 * в пользовательском каталоге ОС вместо PostgreSQL, тот же Liquibase changelog
 * (с context "desktop" — см. changes-desktop-overrides). "user.home" подменён на
 * временную директорию, чтобы тест не трогал реальный ~/Library/Application Support.
 */
@SpringBootTest
@ActiveProfiles("desktop")
class DesktopProfileTest {

	private static String originalUserHome;
	private static Path tempHome;

	@BeforeAll
	static void redirectUserHome() throws IOException {
		originalUserHome = System.getProperty("user.home");
		tempHome = Files.createTempDirectory("furniture-calculator-desktop-profile-test");
		System.setProperty("user.home", tempHome.toString());
	}

	@AfterAll
	static void restoreUserHome() {
		System.setProperty("user.home", originalUserHome);
	}

	@Autowired
	private DoorConfigurationRepository doorConfigurationRepository;

	@Test
	void desktopProfileCreatesFileBasedH2UnderApplicationSupportAndMigratesSuccessfully() {
		Path dbFile = tempHome.resolve("Library/Application Support/FurnitureCalculator/data/furniture-calculator.mv.db");
		assertThat(dbFile).exists();
		assertThatCode(() -> doorConfigurationRepository.findAll()).doesNotThrowAnyException();
	}

}
