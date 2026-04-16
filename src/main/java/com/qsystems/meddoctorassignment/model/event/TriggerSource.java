package com.qsystems.meddoctorassignment.model.event;

/**
 * Источник запуска доменного цикла назначения.
 */
public enum TriggerSource {
    SERVICE_POINT_OPEN,
    SET_WORK_PROFILE,
    USER_SERVICE_POINT_SESSION_START,
    USER_SESSION_READY,
    POLLING
}
