package com.qsystems.meddoctorassignment.config;

/**
 * Стратегия обработки ошибки Orchestra при попытке добавить в маршрут визита услугу,
 * выбранную med-robot, если этой услуги еще нет в unservedVisitServices.
 */
public enum MissingRobotServiceAddFailureMode {

    /**
     * Не останавливать визит: залогировать ошибку POST add-service и продолжить assign/transfer.
     */
    CONTINUE_WITH_ASSIGN,

    /**
     * Пропустить текущий визит в этом цикле.
     */
    SKIP_VISIT,

    /**
     * Пробросить ошибку наверх как критическую ошибку цикла.
     */
    PROPAGATE_ERROR
}
