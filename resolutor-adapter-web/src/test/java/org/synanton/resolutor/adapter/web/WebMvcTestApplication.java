package org.synanton.resolutor.adapter.web;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * Slice-test bootstrap: this module has no {@code @SpringBootApplication}. Imports the REST
 * controllers so {@code @WebMvcTest} can resolve mappings.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@Import({TaskController.class, PlanController.class, GlobalExceptionHandler.class})
class WebMvcTestApplication {}
