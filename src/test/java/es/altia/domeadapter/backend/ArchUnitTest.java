package es.altia.domeadapter.backend;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.GeneralCodingRules;

@AnalyzeClasses(packages = ArchUnitTest.BASE_PACKAGE)
class ArchUnitTest {

    static final String BASE_PACKAGE = "es.altia.domeadapter.backend";

    @ArchTest
    static final ArchRule testClassesShouldResideInTheSamePackageAsImplementation =
            GeneralCodingRules.testClassesShouldResideInTheSamePackageAsImplementation();
    
}

