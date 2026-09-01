package com.sanlam.claims.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.sanlam.claims", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest
{

    @ArchTest
    static final ArchRule controllersStayInTheApiPackage = classes().that()
            .areAnnotatedWith("org.springframework.web.bind.annotation.RestController").should()
            .resideInAPackage("..api..");

    @ArchTest
    static final ArchRule apiDoesNotAccessPersistenceDirectly = noClasses().that().resideInAPackage("..api..").should()
            .dependOnClassesThat().resideInAPackage("com.sanlam.claims.persistence..");

    @ArchTest
    static final ArchRule domainDoesNotDependOnWebApplicationOrIntegrations = noClasses().that()
            .resideInAPackage("..domain..").should().dependOnClassesThat().resideInAnyPackage("com.sanlam.claims.api..",
                    "com.sanlam.claims.application..", "com.sanlam.claims.integration..",
                    "com.sanlam.claims.persistence..");

    @ArchTest
    static final ArchRule requestDtosStayInTheRequestPackage = classes().that().haveSimpleNameEndingWith("Request")
            .should().resideInAPackage("..dto.request..");

    @ArchTest
    static final ArchRule responseDtosStayInTheResponsePackage = classes().that().haveSimpleNameEndingWith("Response")
            .should().resideInAPackage("..dto.response..");

    @ArchTest
    static final ArchRule repositoriesContainOnlyRepositoryInterfaces = classes().that()
            .resideInAPackage("..persistence.repository..").should().beInterfaces().andShould()
            .haveSimpleNameEndingWith("Repository");

    @ArchTest
    static final ArchRule persistenceEntitiesStayOutOfRepositoryPackages = noClasses().that()
            .areAnnotatedWith("jakarta.persistence.Entity").should().resideInAPackage("..repository..");
}
