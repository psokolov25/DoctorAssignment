package com.qsystems.meddoctorassignment.config;

import io.micronaut.context.annotation.ConfigurationProperties;

/**
 * Настройки интеграции Doctor Assistant с внешним сервисом med-robot.
 *
 * <p>Med-robot используется как опциональный внешний селектор оптимальной услуги и очереди.
 * При отключенном флаге {@code enabled} сервис работает по прежней локальной схеме выбора
 * услуги из маршрута визита и доступных врачу очередей.</p>
 */
@ConfigurationProperties("application.med-robot")
public class MedRobotProperties {

  /**
   * Включает обращение к med-robot при выборе услуги и очереди.
   */
  private boolean enabled = false;

  /**
   * Базовый URL med-robot без обязательного завершающего слэша.
   */
  private String url = "http://localhost:8082";

  /**
   * Путь REST endpoint-а med-robot для выбора оптимальной услуги.
   */
  private String optimalServicePath = "/prorobot/optimalqueue/{branchId}/service/{serviceId}";

  /**
   * Имя пользователя для Basic Auth, если REST API med-robot закрыт авторизацией.
   */
  private String username;

  /**
   * Пароль для Basic Auth, если REST API med-robot закрыт авторизацией.
   */
  private String password;

  /**
   * Возвращаться ли к локальному алгоритму, если med-robot недоступен или вернул ошибку.
   */
  private boolean fallbackToLocalOnError = true;

  /**
   * Возвращаться ли к локальному алгоритму, если med-robot не смог выбрать услугу/очередь.
   */
  private boolean fallbackToLocalOnEmptyResponse = true;

  /**
   * Требовать ли, чтобы услуга, выбранная med-robot, входила в доступные услуги текущего врача.
   *
   * <p>По умолчанию выключено: med-robot может выбрать оптимальную очередь в масштабе отделения,
   * а не только текущего рабочего места. Если инсталляции нужен строгий режим «только текущий
   * врач», этот флаг нужно включить.</p>
   */
  private boolean requireDoctorAvailableService = false;

  /**
   * Проверять ли наличие возвращенной med-robot очереди в кэше отделения.
   */
  private boolean requireKnownQueue = true;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getOptimalServicePath() {
    return optimalServicePath;
  }

  public void setOptimalServicePath(String optimalServicePath) {
    this.optimalServicePath = optimalServicePath;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public boolean isFallbackToLocalOnError() {
    return fallbackToLocalOnError;
  }

  public void setFallbackToLocalOnError(boolean fallbackToLocalOnError) {
    this.fallbackToLocalOnError = fallbackToLocalOnError;
  }

  public boolean isFallbackToLocalOnEmptyResponse() {
    return fallbackToLocalOnEmptyResponse;
  }

  public void setFallbackToLocalOnEmptyResponse(boolean fallbackToLocalOnEmptyResponse) {
    this.fallbackToLocalOnEmptyResponse = fallbackToLocalOnEmptyResponse;
  }

  public boolean isRequireDoctorAvailableService() {
    return requireDoctorAvailableService;
  }

  public void setRequireDoctorAvailableService(boolean requireDoctorAvailableService) {
    this.requireDoctorAvailableService = requireDoctorAvailableService;
  }

  public boolean isRequireKnownQueue() {
    return requireKnownQueue;
  }

  public void setRequireKnownQueue(boolean requireKnownQueue) {
    this.requireKnownQueue = requireKnownQueue;
  }
}
