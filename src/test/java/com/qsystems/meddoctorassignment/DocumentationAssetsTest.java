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

  private static final List<DiagramAsset> DIAGRAMS =
      Arrays.asList(
          diagram("architecture-overview", "Общая архитектура"),
          diagram("package-dependency-map", "Диаграмма пакетов и направлений зависимостей"),
          diagram("domain-class-diagram", "Диаграмма классов доменного контура назначения"),
          diagram("assignment-sequence", "Последовательность назначения"),
          diagram("med-robot-selection-sequence", "Подробная последовательность выбора через med-robot"),
          diagram("cache-refresh-sequence", "Пересборка кэша"),
          diagram("polling-reconciliation-sequence", "Последовательность плановой reconciliation-обработки"),
          diagram("deployment-view", "Схема внедрения"),
          diagram("orchestration-swimlane", "Процесс работы по зонам ответственности"),
          diagram("visit-lifecycle-state", "Состояния визита"),
          diagram("med-robot-fallback-decision", "Дерево решений"),
          diagram("operation-modes-map", "Карта режимов запуска"),
          diagram("data-contract-map", "Карта данных и REST-контракта"),
          diagram("failure-recovery-flow", "Отказы и восстановление"),
          diagram("rest-mutation-flow", "REST-мутации Orchestra"),
          diagram("observability-checklist", "Наблюдаемость и эксплуатационная проверка"));

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
    Path sharedStyle = Paths.get("docs/plantuml/common-style.puml");
    Assertions.assertTrue(Files.exists(sharedStyle), "Общий стиль PlantUML не найден: " + sharedStyle);

    String sharedStyleText = new String(Files.readAllBytes(sharedStyle), StandardCharsets.UTF_8);
    Assertions.assertTrue(
        sharedStyleText.contains("Segoe UI"),
        "В общем стиле PlantUML не задан актуальный читаемый шрифт: " + sharedStyle);
    Assertions.assertTrue(
        sharedStyleText.contains("#F8FAFC"),
        "В общем стиле PlantUML не задан общий фон диаграммы: " + sharedStyle);

    for (DiagramAsset diagram : DIAGRAMS) {
      Path file = diagram.plantumlPath();
      String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
      Assertions.assertTrue(
          text.contains("!include common-style.puml"),
          "Диаграмма не подключает общий стиль PlantUML: " + file);
      Assertions.assertTrue(containsCyrillic(text), "Диаграмма должна содержать русские пояснения: " + file);
    }
  }

  @Test
  void svgDiagramsContainReadableRussianTitles() throws IOException {
    for (DiagramAsset diagram : DIAGRAMS) {
      assertContains(diagram.svgPath(), diagram.expectedSvgTitle);
    }
  }

  @Test
  void readmeReferencesAllPreparedDiagramsAndSources() throws IOException {
    String readme = new String(Files.readAllBytes(Paths.get("README.md")), StandardCharsets.UTF_8);
    assertDocumentationReferencesAllPreparedDiagrams("README.md", readme);
  }

  @Test
  void medRobotGuideReferencesAllPreparedDiagrams() throws IOException {
    String guide =
        new String(Files.readAllBytes(Paths.get("MED_ROBOT_INTEGRATION.md")), StandardCharsets.UTF_8);
    for (DiagramAsset diagram : DIAGRAMS) {
      Assertions.assertTrue(
          guide.contains(diagram.svgPath().toString().replace('\\', '/')),
          "MED_ROBOT_INTEGRATION.md не ссылается на SVG: " + diagram.svgPath());
    }
  }

  private static void assertDocumentationReferencesAllPreparedDiagrams(
      String documentName, String documentText) {
    for (DiagramAsset diagram : DIAGRAMS) {
      String svgPath = diagram.svgPath().toString().replace('\\', '/');
      String pumlPath = diagram.plantumlPath().toString().replace('\\', '/');
      Assertions.assertTrue(
          documentText.contains(svgPath), documentName + " не ссылается на SVG: " + svgPath);
      Assertions.assertTrue(
          documentText.contains(pumlPath), documentName + " не ссылается на PlantUML: " + pumlPath);
    }
  }

  private static List<Path> allDocumentationFiles() {
    java.util.ArrayList<Path> files = new java.util.ArrayList<Path>();
    files.addAll(TEXT_DOCUMENTATION_FILES);
    for (DiagramAsset diagram : DIAGRAMS) {
      files.add(diagram.plantumlPath());
      files.add(diagram.svgPath());
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

  private static DiagramAsset diagram(String name, String expectedSvgTitle) {
    return new DiagramAsset(name, expectedSvgTitle);
  }

  private static final class DiagramAsset {
    private final String name;
    private final String expectedSvgTitle;

    private DiagramAsset(String name, String expectedSvgTitle) {
      this.name = name;
      this.expectedSvgTitle = expectedSvgTitle;
    }

    private Path plantumlPath() {
      return Paths.get("docs/plantuml/" + name + ".puml");
    }

    private Path svgPath() {
      return Paths.get("docs/diagrams/" + name + ".svg");
    }
  }
}
