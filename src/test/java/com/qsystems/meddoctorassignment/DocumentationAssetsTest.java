package com.qsystems.meddoctorassignment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** Проверяет, что документационные диаграммы читаемы и не содержат искаженного текста. */
public class DocumentationAssetsTest {

  private static final List<Path> DOCUMENTATION_FILES =
      Arrays.asList(
          Paths.get("README.md"),
          Paths.get("MED_ROBOT_INTEGRATION.md"),
          Paths.get("docs/plantuml/architecture-overview.puml"),
          Paths.get("docs/plantuml/assignment-sequence.puml"),
          Paths.get("docs/plantuml/cache-refresh-sequence.puml"),
          Paths.get("docs/plantuml/deployment-view.puml"),
          Paths.get("docs/diagrams/architecture-overview.svg"),
          Paths.get("docs/diagrams/assignment-sequence.svg"),
          Paths.get("docs/diagrams/cache-refresh-sequence.svg"),
          Paths.get("docs/diagrams/deployment-view.svg"));

  @Test
  void documentationFilesAreUtf8AndDoNotContainMojibake() throws IOException {
    for (Path file : DOCUMENTATION_FILES) {
      Assertions.assertTrue(Files.exists(file), "Документационный файл не найден: " + file);
      String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
      Assertions.assertFalse(text.contains("�"), "Файл содержит replacement character: " + file);
      Assertions.assertFalse(text.contains("����"), "Файл содержит mojibake-последовательность: " + file);
    }
  }

  @Test
  void plantumlDiagramsUseRussianExplanationsAndSharedVisualStyle() throws IOException {
    for (Path file : DOCUMENTATION_FILES) {
      if (!file.toString().endsWith(".puml")) {
        continue;
      }
      String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
      Assertions.assertTrue(text.contains("DejaVu Sans"), "Не задан читаемый шрифт: " + file);
      Assertions.assertTrue(text.contains("#F8FAFC"), "Не задан общий фон диаграммы: " + file);
      Assertions.assertTrue(containsCyrillic(text), "Диаграмма должна содержать русские пояснения: " + file);
    }
  }

  @Test
  void svgDiagramsContainReadableRussianTitles() throws IOException {
    assertContains(Paths.get("docs/diagrams/architecture-overview.svg"), "Общая архитектура");
    assertContains(Paths.get("docs/diagrams/assignment-sequence.svg"), "Последовательность назначения");
    assertContains(Paths.get("docs/diagrams/cache-refresh-sequence.svg"), "Пересборка кэша");
    assertContains(Paths.get("docs/diagrams/deployment-view.svg"), "Схема внедрения");
  }

  private static void assertContains(Path file, String expectedText) throws IOException {
    String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    Assertions.assertTrue(
        text.contains(expectedText),
        "SVG-диаграмма не содержит ожидаемый русский заголовок: " + file);
  }

  private static boolean containsCyrillic(String text) {
    for (int i = 0; i < text.length(); i++) {
      char ch = text.charAt(i);
      if (ch >= '\u0400' && ch <= '\u04FF') {
        return true;
      }
    }
    return false;
  }
}
