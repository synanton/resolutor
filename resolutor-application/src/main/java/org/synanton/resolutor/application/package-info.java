/**
 * Root package for Resolutor application services and hexagonal ports.
 *
 * <p>Contains inbound ports (driven by adapters) under {@code port.in}, outbound ports (driven by
 * application) under {@code port.out}, and orchestration services that compose them. This module
 * depends on the domain module only - never on adapters.
 */
@NullMarked
package org.synanton.resolutor.application;

import org.jspecify.annotations.NullMarked;
