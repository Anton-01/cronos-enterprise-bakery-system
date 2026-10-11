package com.ninsky.cronos.architecture;

import com.ninsky.cronos.eventing.domain.DomainEvent;
import com.ninsky.cronos.eventing.domain.EventType;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noConstructors;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

/**
 * Architecture rules of auth-quotes-calendar-notifications §11.3. Rules the whole code base already meets apply
 * everywhere; the stricter layering rules apply to the modules built on the outbox (eventing, mail, identity, quote,
 * calendar, notification), the older packages keep their own conventions until they are migrated.
 */
@AnalyzeClasses(packages = "com.ninsky.cronos", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureRulesTest {

    static final String[] EVENT_DRIVEN_MODULES = {"com.ninsky.cronos.eventing..", "com.ninsky.cronos.mail..",
            "com.ninsky.cronos.identity..", "com.ninsky.cronos.quote..", "com.ninsky.cronos.calendar..", "com.ninsky.cronos.notification.."};
    static final String[] EVENT_DRIVEN_DOMAINS = {"com.ninsky.cronos.eventing.domain..", "com.ninsky.cronos.mail.domain..",
            "com.ninsky.cronos.identity.domain..", "com.ninsky.cronos.quote.domain..", "com.ninsky.cronos.calendar.domain..",
            "com.ninsky.cronos.notification.domain.."};

    /** R1: constructor injection only. */
    @ArchTest
    static final ArchRule noAutowiredFields = noFields().should().beAnnotatedWith(Autowired.class)
            .because("collaborators are final and constructor-injected");

    @ArchTest
    static final ArchRule noAutowiredMethods = noMethods().should().beAnnotatedWith(Autowired.class)
            .because("setter injection hides required dependencies");

    @ArchTest
    static final ArchRule noAutowiredConstructors = noConstructors().should().beAnnotatedWith(Autowired.class)
            .because("a single constructor is injected implicitly");

    @ArchTest
    static final ArchRule injectedFieldsAreFinal = fields().that().areDeclaredInClassesThat().areAnnotatedWith(Service.class)
            .and().areDeclaredInClassesThat().resideInAnyPackage(EVENT_DRIVEN_MODULES)
            .and().areNotStatic().should().beFinal()
            .because("services hold only their injected collaborators");

    @ArchTest
    static final ArchRule domainIsFrameworkFree = noClasses().that().resideInAnyPackage(EVENT_DRIVEN_DOMAINS)
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta.persistence..")
            .because("the domain is plain Java");

    @ArchTest
    static final ArchRule controllersDoNotReachIntoRepositories = noClasses().that().areAnnotatedWith(RestController.class)
            .and().resideInAnyPackage(EVENT_DRIVEN_MODULES)
            .should().dependOnClassesThat().haveSimpleNameEndingWith("CustomRepository")
            .because("controllers are thin: they call application services");

    /** R4: side effects through the outbox. */
    @ArchTest
    static final ArchRule eventsAreRecords = classes().that().implement(DomainEvent.class).and().areNotInterfaces()
            .should().beRecords().andShould().beAnnotatedWith(EventType.class)
            .because("events are immutable and carry their stable type string");

    @ArchTest
    static final ArchRule noAsyncSideEffects = noMethods().should().beAnnotatedWith(Async.class)
            .because("background work goes through the transactional outbox, which survives a crash");

    @ArchTest
    static final ArchRule noAfterCommitListenersInEventDrivenModules = noMethods().that().areDeclaredInClassesThat()
            .resideInAnyPackage(EVENT_DRIVEN_MODULES).and().areDeclaredInClassesThat().haveSimpleNameNotEndingWith("Provider")
            .should().beAnnotatedWith(TransactionalEventListener.class)
            .because("an after-commit listener loses its effect if the process stops; outbox handlers retry (cache providers "
                    + "evict their per-instance cache after commit, which must happen on every node)");

    @ArchTest
    static final ArchRule gatewaysOnlyBehindTheOutbox = noClasses().that().resideInAPackage("..application..")
            .and().doNotHaveSimpleName("PushService")
            .should().dependOnClassesThat().haveSimpleNameEndingWith("Gateway")
            .because("mail and push gateways are called by outbox consumers only (the push test send is the documented exception)");
}
