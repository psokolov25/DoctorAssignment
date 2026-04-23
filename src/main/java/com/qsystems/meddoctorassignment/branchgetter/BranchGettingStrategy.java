package com.qsystems.meddoctorassignment.branchgetter;

import java.util.Collection;

/** Стратегия выбора branch id, для которых сервис должен инициализировать и поддерживать кэш. */
public interface BranchGettingStrategy {

  /** Проверяет, применима ли стратегия к значению свойства {@code branches-for-cache}. */
  boolean supports(String branchesForCache);

  /** Возвращает branch id, которые нужно кэшировать. */
  Collection<Integer> getBranchIds();
}
