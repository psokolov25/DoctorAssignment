package com.qsystems.meddoctorassignment;

import io.micronaut.runtime.Micronaut;

/**
 * Точка входа Micronaut-приложения.
 *
 * <p>Сервис запускается как отдельный процесс и после старта поднимает HTTP endpoint-ы,
 * инициализирует кэши метаданных Orchestra и при включенной интеграции подключается к событийной
 * SockJS/STOMP-шине Orchestra.
 */
public class DoctorAssignmentApplication {

  public static void main(String[] args) {
    Micronaut.run(DoctorAssignmentApplication.class, args);
  }
}
