package com.qsystems.meddoctorassignment;

import com.qsystems.meddoctorassignment.adapter.medrobot.MedRobotRestClient;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Post;
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

    Method method =
        MedRobotRestClient.class.getMethod("selectOptimalService", int.class, int.class, Set.class);
    Post post = method.getAnnotation(Post.class);
    Assertions.assertNotNull(post);
    Assertions.assertEquals("${application.med-robot.optimal-service-path}", annotationValue(post, "value"));
  }

  @Test
  void medRobotClientSendsUnservedServiceIdsAsRequestBody() throws Exception {
    Method method =
        MedRobotRestClient.class.getMethod("selectOptimalService", int.class, int.class, Set.class);
    Parameter[] parameters = method.getParameters();

    Assertions.assertEquals(Set.class, parameters[2].getType());
    Assertions.assertTrue(hasAnnotation(parameters[2], Body.class));
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
