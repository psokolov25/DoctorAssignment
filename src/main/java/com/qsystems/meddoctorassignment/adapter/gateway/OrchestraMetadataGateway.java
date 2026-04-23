package com.qsystems.meddoctorassignment.adapter.gateway;

import com.qsystems.meddoctorassignment.adapter.orchestra.dto.ServiceData;
import com.qsystems.meddoctorassignment.adapter.orchestra.dto.ServicePointData;
import com.qsystems.meddoctorassignment.adapter.orchestra.dto.SmallBranch;
import com.qsystems.meddoctorassignment.adapter.orchestra.dto.TinyQueue;
import com.qsystems.meddoctorassignment.adapter.orchestra.dto.WorkProfileData;
import java.util.List;

/**
 * Абстракция над чтением справочных данных Orchestra, необходимых для принятия решений о назначении
 * врача визиту.
 *
 * <p>Через этот gateway слой домена и кэширования получает только стабильные справочные сущности:
 * отделения, услуги, очереди, рабочие профили и точки обслуживания.
 */
public interface OrchestraMetadataGateway {

  /** Возвращает список всех отделений, доступных через configuration API Orchestra. */
  List<SmallBranch> getAllBranches();

  /**
   * Читает все услуги отделения.
   *
   * @param branchId идентификатор отделения Orchestra
   * @return список услуг, доступных в отделении
   */
  List<ServiceData> getServicesFromBranch(int branchId);

  /** Возвращает очередь, в которой обслуживается указанная услуга. */
  TinyQueue getQueueForServiceInBranch(int branchId, int serviceId);

  /** Возвращает точки обслуживания отделения. */
  List<ServicePointData> getServicePointsFromBranch(int branchId);

  /** Возвращает рабочие профили отделения. */
  List<WorkProfileData> getWorkProfilesFromBranch(int branchId);

  /** Возвращает очереди, привязанные к рабочему профилю. */
  List<TinyQueue> getQueuesForWorkProfileInBranch(int branchId, int workProfileId);

  /** Возвращает полный список очередей отделения. */
  List<TinyQueue> getAllQueuesInBranch(int branchId);
}
