package com.qsystems.meddoctorassignment.adapter.gateway;

import com.qsystems.meddoctorassignment.domain.model.VisitDetails;
import com.qsystems.meddoctorassignment.domain.model.VisitSummary;
import java.util.List;
import java.util.Optional;

/**
 * Абстракция над операциями жизненного цикла визита.
 *
 * <p>Выделена отдельно от чтения справочников, потому что набор endpoint-ов для работы с
 * визитом обычно зависит от конкретной инсталляции Orchestra и требует отдельной верификации.</p>
 */
public interface VisitWorkflowGateway {

    /**
     * Возвращает визиты, ожидающие в указанной очереди.
     */
    List<VisitSummary> getWaitingVisits(int branchId, int queueId);

    /**
     * Читает детальное состояние визита, включая непройденные услуги.
     */
    VisitDetails getVisitDetails(int branchId, long visitId);

    /**
     * Добавляет услугу в маршрут визита, если выбранная роботом услуга отсутствует
     * среди непройденных услуг визита.
     */
    void addServiceToVisit(int branchId, long visitId, int serviceId);

    /**
     * Назначает визиту услугу врача.
     */
    void assignServiceToVisit(int branchId, long visitId, int serviceId, int staffId, long servicePointId);

    /**
     * Переводит визит из одной очереди в другую.
     */
    void transferVisitToQueue(int branchId, long visitId, int sourceQueueId, int targetQueueId);

    /**
     * Повторно читает визит по id.
     *
     * <p>Используется для defensive recheck перед переводом и для верификации фактического результата.</p>
     */
    Optional<VisitSummary> findVisit(int branchId, long visitId);
}
