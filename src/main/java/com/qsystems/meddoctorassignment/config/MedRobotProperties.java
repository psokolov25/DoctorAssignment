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
   * Формат тела REST-запроса к med-robot.
   *
   * <p>По умолчанию используется старый контракт: JSON-массив идентификаторов непройденных
   * услуг. Для второй REST-точки med-robot с {@code Content-Type: text/plain} нужно указать
   * {@code TICKET_NUMBER_PLAIN_TEXT}; тогда в тело будет передан номер талона визита.</p>
   */
  private MedRobotRequestBodyMode requestBodyMode =
      MedRobotRequestBodyMode.UNSERVED_SERVICE_IDS_JSON_ARRAY;

  /**
   * Значение query-параметра policy для text/plain REST-точки med-robot.
   */
  private String plainTextPolicy = "default";

  /**
   * Имя пользователя для Basic Auth, если REST API med-robot закрыт авторизацией.
   */
  private String username;

  /**
   * Пароль для Basic Auth, если REST API med-robot закрыт авторизацией.
   */
  private String password;

  /**
   * Действие при HTTP/сетевой/контрактной ошибке med-robot.
   *
   * <p>{@code FALLBACK_TO_LOCAL} продолжает обработку текущего визита по старой локальной
   * схеме без робота. {@code SKIP_VISIT} пропускает текущий визит в этом цикле и переходит
   * к следующему визиту очереди.
   */
  private MedRobotErrorHandlingMode errorHandlingMode =
      MedRobotErrorHandlingMode.FALLBACK_TO_LOCAL;

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

  public MedRobotRequestBodyMode getRequestBodyMode() {
    return requestBodyMode;
  }

  public void setRequestBodyMode(MedRobotRequestBodyMode requestBodyMode) {
    this.requestBodyMode =
        requestBodyMode != null ? requestBodyMode : MedRobotRequestBodyMode.UNSERVED_SERVICE_IDS_JSON_ARRAY;
  }

  public String getPlainTextPolicy() {
    return plainTextPolicy;
  }

  public void setPlainTextPolicy(String plainTextPolicy) {
    this.plainTextPolicy =
        plainTextPolicy != null && !plainTextPolicy.trim().isEmpty()
            ? plainTextPolicy.trim()
            : "default";
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

  public MedRobotErrorHandlingMode getErrorHandlingMode() {
    return errorHandlingMode;
  }

  public void setErrorHandlingMode(MedRobotErrorHandlingMode errorHandlingMode) {
    this.errorHandlingMode =
        errorHandlingMode != null ? errorHandlingMode : MedRobotErrorHandlingMode.FALLBACK_TO_LOCAL;
  }

  /**
   * Deprecated алиас старого boolean-свойства {@code fallback-to-local-on-error}.
   *
   * <p>Оставлен для обратной совместимости конфигураций: {@code true} соответствует
   * {@code FALLBACK_TO_LOCAL}, {@code false} — {@code SKIP_VISIT}. Для новых конфигураций
   * используйте {@code error-handling-mode}.
   */
  public boolean isFallbackToLocalOnError() {
    return MedRobotErrorHandlingMode.FALLBACK_TO_LOCAL.equals(errorHandlingMode);
  }

  /**
   * Deprecated алиас старого boolean-свойства {@code fallback-to-local-on-error}.
   */
  public void setFallbackToLocalOnError(boolean fallbackToLocalOnError) {
    this.errorHandlingMode =
        fallbackToLocalOnError
            ? MedRobotErrorHandlingMode.FALLBACK_TO_LOCAL
            : MedRobotErrorHandlingMode.SKIP_VISIT;
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
