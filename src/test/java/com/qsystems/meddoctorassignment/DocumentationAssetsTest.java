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

  private static final List<String> DIAGRAM_NAMES =
      Arrays.asList(
          "architecture-overview",
          "assignment-sequence",
          "cache-refresh-sequence",
          "deployment-view",
          "orchestration-swimlane",
          "visit-lifecycle-state",
          "med-robot-fallback-decision",
          "operation-modes-map",
          "data-contract-map",
          "failure-recovery-flow",
          "rest-mutation-flow",
          "observability-checklist");

  private static final List<Path> TEXT_DOCUMENTATION_FILES =
      Arrays.asList(Paths.get("README.md"), Paths.get("MED_ROBOT_INTEGRATION.md"));

  @Test
  void documentationFilesAreUtf8AndDoNotContainMojibake() throws IOException {
    for (Path file : allDocumentationFiles()) {
      Assertions.assertTrue(Files.exists(file), "Документационный файл не найден: " + file);
      String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
      Assertions.assertFalse(text.contains("�"), "Файл содержит replacement character: " + file);
      Assertions.assertFalse(text.contains("����"), "Файл содержит mojibake-последовательность: " + file);
    }
  }

  @Test
  void plantumlDiagramsUseRussianExplanationsAndSharedVisualStyle() throws IOException {
    for (String diagramName : DIAGRAM_NAMES) {
      Path file = Paths.get("docs/plantuml/" + diagramName + ".puml");
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
    assertContains(Paths.get("docs/diagrams/orchestration-swimlane.svg"), "Процесс работы по зонам ответственности");
    assertContains(Paths.get("docs/diagrams/visit-lifecycle-state.svg"), "Состояния визита");
    assertContains(Paths.get("docs/diagrams/med-robot-fallback-decision.svg"), "Дерево решений");
    assertContains(Paths.get("docs/diagrams/operation-modes-map.svg"), "Карта режимов запуска");
    assertContains(Paths.get("docs/diagrams/data-contract-map.svg"), "Карта данных и REST-контракта");
    assertContains(Paths.get("docs/diagrams/failure-recovery-flow.svg"), "Отказы и восстановление");
    assertContains(Paths.get("docs/diagrams/rest-mutation-flow.svg"), "REST-мутации Orchestra");
    assertContains(Paths.get("docs/diagrams/observability-checklist.svg"), "Наблюдаемость и эксплуатационная проверка");
  }

  @Test
  void readmeReferencesAllPreparedSvgDiagrams() throws IOException {
    String readme = new String(Files.readAllBytes(Paths.get("README.md")), StandardCharsets.UTF_8);
    for (String diagramName : DIAGRAM_NAMES) {
      String svgPath = "docs/diagrams/" + diagramName + ".svg";
      String pumlPath = "docs/plantuml/" + diagramName + ".puml";
      Assertions.assertTrue(readme.contains(svgPath), "README.md не ссылается на SVG: " + svgPath);
      Assertions.assertTrue(readme.contains(pumlPath), "README.md не ссылается на PlantUML: " + pumlPath);
    }
  }

  private static List<Path> allDocumentationFiles() {
    java.util.ArrayList<Path> files = new java.util.ArrayList<Path>();
    files.addAll(TEXT_DOCUMENTATION_FILES);
    for (String diagramName : DIAGRAM_NAMES) {
      files.add(Paths.get("docs/plantuml/" + diagramName + ".puml"));
      files.add(Paths.get("docs/diagrams/" + diagramName + ".svg"));
    }
    return files;
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
