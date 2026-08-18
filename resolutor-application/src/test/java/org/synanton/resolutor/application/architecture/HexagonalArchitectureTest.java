package org.synanton.resolutor.application.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Enforces hexagonal boundaries described in {@code docs/implementation-plan.md} §3.
 *
 * <p>These rules run against the classpath of this module (domain + application). Cross-module
 * boundary checks against adapter modules land in the {@code resolutor-app} module where all
 * modules are on the classpath together.
 */
@AnalyzeClasses(
    packages = "org.synanton.resolutor",
    importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {

  @ArchTest
  static final ArchRule domain_does_not_depend_on_spring =
      noClasses()
          .that()
          .resideInAPackage("org.synanton.resolutor.domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("org.springframework..", "jakarta..")
          .because("domain is framework-free (see docs/implementation-plan.md §3)");

  @ArchTest
  static final ArchRule domain_does_not_depend_on_application =
      noClasses()
          .that()
          .resideInAPackage("org.synanton.resolutor.domain..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("org.synanton.resolutor.application..")
          .because("dependency direction is inward only");

  @ArchTest
  static final ArchRule application_does_not_depend_on_adapters =
      noClasses()
          .that()
          .resideInAPackage("org.synanton.resolutor.application..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("org.synanton.resolutor.adapter..")
          .because("application depends on ports, adapters implement them");
}
