package com.qsystems.meddoctorassignment.domain.service;

import com.qsystems.meddoctorassignment.cache.model.BranchAssignmentCache;
import com.qsystems.meddoctorassignment.domain.model.SelectedDoctorService;
import com.qsystems.meddoctorassignment.domain.model.VisitDetails;
import java.util.Optional;
import java.util.Set;

/**
 * Контракт выбора итоговой услуги врача для конкретного визита.
 *
 * <p>Сервис инкапсулирует стратегию принятия решения:
 *
 * <ul>
 *   <li>локальный deterministic-matching по пересечению услуг визита и врача;</li>
 *   <li>опциональное обращение к med-robot при включенной интеграции;</li>
 *   <li>fallback к локальному алгоритму при недоступности внешнего решателя.</li>
 * </ul>
 *
 * <p>Результат возвращается как {@link Optional}, чтобы вызывающий код мог явно обработать случай
 * "подходящая услуга не найдена" без исключений.</p>
 */
public interface DoctorServiceSelectionService {

  /**
   * Выбирает итоговую пару {@code serviceId/queueId} для дальнейшего mutation-flow.
   *
   * @param visitDetails детальный снимок маршрута визита (текущая услуга, непройденные услуги, queue)
   * @param doctorAvailableServices множество {@code serviceId}, доступных врачу в текущем контексте
   * @param branchCache кэш отделения с соответствиями услуг, очередей и внешних ключей
   * @return выбранная услуга и очередь либо {@link Optional#empty()}, если подходящего варианта нет
   */
  Optional<SelectedDoctorService> select(
      VisitDetails visitDetails,
      Set<Integer> doctorAvailableServices,
      BranchAssignmentCache branchCache);
}
