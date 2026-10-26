package org.synanton.resolutor.adapter.persistence.jpa;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

/** Minimal Spring Boot configuration so {@code @DataJpaTest} can bootstrap this adapter slice. */
@SpringBootConfiguration
@EnableAutoConfiguration
class TestApplication {}
