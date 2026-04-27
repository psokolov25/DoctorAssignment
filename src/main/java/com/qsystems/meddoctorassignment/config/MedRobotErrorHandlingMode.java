package com.qsystems.meddoctorassignment.config;

/**
 * Действие Doctor Assistant при HTTP/сетевой/контрактной ошибке med-robot.
 */
public enum MedRobotErrorHandlingMode {
  /**
   * Продолжить обработку текущего визита по старой локальной схеме без med-robot.
   */
  FALLBACK_TO_LOCAL,

  /**
   * Пропустить текущий визит в этом цикле и перейти к следующему визиту очереди.
   */
  SKIP_VISIT
}
