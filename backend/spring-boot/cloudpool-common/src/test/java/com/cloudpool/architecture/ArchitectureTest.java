package com.cloudpool.architecture;

import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.cloudpool")
public class ArchitectureTest {

    @ArchTest
    static final ArchRule services_should_not_access_controllers =
            noClasses().that().resideInAPackage("..service..")
                    .should().accessClassesThat().resideInAPackage("..controller..")
                    .because("Services should not depend on the web layer")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule interfaces_should_not_have_names_ending_with_the_word_interface =
            noClasses().that().areInterfaces()
                    .should().haveSimpleNameEndingWith("Interface")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule services_should_be_annotated_with_service =
            classes().that().resideInAPackage("..service..")
                    .and().areNotInterfaces()
                    .and().haveSimpleNameEndingWith("Service")
                    .should().beAnnotatedWith(org.springframework.stereotype.Service.class)
                    .because("Service classes should use @Service annotation")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule utility_classes_should_be_final =
            classes().that().resideInAPackage("..util..")
                    .and().haveSimpleNameEndingWith("Utils")
                    .should().haveModifier(JavaModifier.FINAL)
                    .because("Utility classes with only static methods should be declared final")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule repositories_should_be_named_repository =
            classes().that().resideInAPackage("..repository..")
                    .and().areInterfaces()
                    .should().haveSimpleNameContaining("Repository")
                    .because("Repository interfaces should follow naming convention")
                    .allowEmptyShould(true);
}