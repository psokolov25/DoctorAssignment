package com.qsystems.meddoctorassignment;

import com.qsystems.meddoctorassignment.adapter.medrobot.MedRobotRestClient;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.client.annotation.Client;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** Фиксирует REST-контракт, через который Doctor Assistant обращается к med-robot. */
public class MedRobotRestClientContractTest {

  @Test
  void medRobotClientUsesConfiguredBaseUrlAndConfiguredOptimalServicePath() throws Exception {
    Client client = MedRobotRestClient.class.getAnnotation(Client.class);
    Assertions.assertNotNull(client);
    Assertions.assertEquals("${application.med-robot.url}", annotationValue(client, "value"));

    Method jsonMethod =
        MedRobotRestClient.class.getMethod(
            "selectOptimalServiceByUnservedServices", int.class, int.class, Set.class);
    Method plainTextMethod =
        MedRobotRestClient.class.getMethod(
            "selectOptimalServiceByPlainText", int.class, int.class, String.class, String.class);

    assertPostUsesConfiguredPath(jsonMethod);
    assertPostUsesConfiguredPath(plainTextMethod);
  }

  @Test
  void medRobotClientSendsUnservedServiceIdsAsJsonRequestBody() throws Exception {
    Method method =
        MedRobotRestClient.class.getMethod(
            "selectOptimalServiceByUnservedServices", int.class, int.class, Set.class);
    Parameter[] parameters = method.getParameters();

    Assertions.assertEquals(Set.class, parameters[2].getType());
    Assertions.assertTrue(hasAnnotation(parameters[2], Body.class));
    Assertions.assertEquals(
        MediaType.APPLICATION_JSON, annotationValue(method.getAnnotation(Produces.class), "value"));
    Assertions.assertEquals(
        MediaType.APPLICATION_JSON, annotationValue(method.getAnnotation(Consumes.class), "value"));
  }

  @Test
  void medRobotClientSendsTicketNumberAsPlainTextRequestBodyWithPolicyQueryParam() throws Exception {
    Method method =
        MedRobotRestClient.class.getMethod(
            "selectOptimalServiceByPlainText", int.class, int.class, String.class, String.class);
    Parameter[] parameters = method.getParameters();

    Assertions.assertEquals(String.class, parameters[2].getType());
    Assertions.assertTrue(hasAnnotation(parameters[2], Body.class));
    Assertions.assertEquals(String.class, parameters[3].getType());
    Assertions.assertTrue(hasAnnotation(parameters[3], QueryValue.class));
    Assertions.assertEquals(
        MediaType.TEXT_PLAIN, annotationValue(method.getAnnotation(Produces.class), "value"));
    Assertions.assertEquals(
        MediaType.APPLICATION_JSON, annotationValue(method.getAnnotation(Consumes.class), "value"));
  }

  private static void assertPostUsesConfiguredPath(Method method) throws Exception {
    Post post = method.getAnnotation(Post.class);
    Assertions.assertNotNull(post);
    Assertions.assertEquals("${application.med-robot.optimal-service-path}", annotationValue(post, "value"));
  }

  private static String annotationValue(Annotation annotation, String methodName) throws Exception {
    Object value = annotation.annotationType().getMethod(methodName).invoke(annotation);
    if (value instanceof String) {
      return (String) value;
    }
    if (value instanceof String[]) {
      String[] values = (String[]) value;
      return values.length == 0 ? "" : values[0];
    }
    return String.valueOf(value);
  }

  private static boolean hasAnnotation(Parameter parameter, Class<? extends Annotation> type) {
    for (Annotation annotation : parameter.getAnnotations()) {
      if (type.equals(annotation.annotationType())) {
        return true;
      }
    }
    return false;
  }
}
