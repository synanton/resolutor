package org.synanton.resolutor.domain.resource;

import java.util.Objects;

/**
 * Opaque {@code {class, id}} pair identifying a business resource.
 *
 * <p>Resolutor never interprets the contents; equality is purely structural. The domain that
 * implements {@code ResourceGraphPort} owns the semantics.
 */
public record Resource(String resourceClass, String resourceId) {

  public Resource {
    Objects.requireNonNull(resourceClass, "resourceClass");
    if (resourceClass.isBlank())
      throw new IllegalArgumentException("resourceClass must not be blank");
    Objects.requireNonNull(resourceId, "resourceId");
    if (resourceId.isBlank()) throw new IllegalArgumentException("resourceId must not be blank");
  }

  public static Resource of(String resourceClass, String resourceId) {
    return new Resource(resourceClass, resourceId);
  }
}
