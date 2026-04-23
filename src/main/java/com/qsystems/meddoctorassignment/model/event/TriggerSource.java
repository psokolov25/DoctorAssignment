package com.qsystems.meddoctorassignment.model.event;

/**
 * Источник запуска доменного цикла назначения.
 *
 * <p>Значения enum отражают не только сырые события Orchestra, но и внутренние составные trigger-ы
 * сервиса, такие как {@link #USER_SESSION_READY} и {@link #WORK_PROFILE_EXPANDED}. Это позволяет в
 * логах и инцидентах быстро понять, какой именно путь привел к mutating workflow.
 */
public enum TriggerSource {
  /** Сырое событие открытия точки обслуживания; по умолчанию используется только диагностически. */
  SERVICE_POINT_OPEN,
  /** Сырое событие смены рабочего профиля. */
  SET_WORK_PROFILE,
  /** Сырое событие начала пользовательской service point session. */
  USER_SERVICE_POINT_SESSION_START,
  /**
   * Внутренний составной trigger: USER_SERVICE_POINT_SESSION_START + связанный SET_WORK_PROFILE.
   */
  USER_SESSION_READY,
  /** Внутренний trigger расширения набора услуг во время уже активной пользовательской сессии. */
  WORK_PROFILE_EXPANDED,
  /** Периодический fallback-цикл без websocket события. */
  POLLING
}
