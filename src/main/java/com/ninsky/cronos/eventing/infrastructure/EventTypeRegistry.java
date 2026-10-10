package com.ninsky.cronos.eventing.infrastructure;

import com.ninsky.cronos.eventing.domain.DomainEvent;
import com.ninsky.cronos.eventing.domain.EventType;
import com.ninsky.cronos.eventing.domain.RetrySchedule;
import com.ninsky.cronos.eventing.domain.Sensitive;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Bidirectional map between event classes and their {@link EventType} strings, built once at startup by
 * scanning {@code com.ninsky.cronos}. Boot fails fast on a duplicate or malformed type, on an event that is
 * not a record, and {@link #registrationOf(DomainEvent)} fails on an unannotated class.
 */
@Component
public class EventTypeRegistry {

    static final String BASE_PACKAGE = "com.ninsky.cronos";
    private static final Pattern TYPE_SHAPE = Pattern.compile("^[a-z]+(\\.[a-z][a-z-]*)+$");

    /**
     * One registered event class.
     *
     * @param sensitiveComponents record components annotated {@link Sensitive}
     */
    public record Registration(String type, Class<? extends DomainEvent> eventClass, RetrySchedule retry,
                               Set<String> sensitiveComponents) {
        public Registration {
            sensitiveComponents = Set.copyOf(sensitiveComponents);
        }
    }

    private final Map<Class<?>, Registration> byClass;
    private final Map<String, Registration> byType;

    /** Scans the application packages. */
    public EventTypeRegistry() {
        this(scan());
    }

    private EventTypeRegistry(Collection<Class<? extends DomainEvent>> classes) {
        Map<String, Registration> types = classes.stream()
                .map(EventTypeRegistry::register)
                .collect(Collectors.toMap(Registration::type, Function.identity(), (a, b) -> {
                    throw new IllegalStateException("Event type " + a.type() + " is declared by " + a.eventClass().getName()
                            + " and " + b.eventClass().getName());
                }, TreeMap::new));
        this.byType = Map.copyOf(types);
        this.byClass = types.values().stream()
                .collect(Collectors.<Registration, Class<?>, Registration>toUnmodifiableMap(Registration::eventClass, Function.identity()));
    }

    /** Registry of exactly {@code classes} (tests). */
    public static EventTypeRegistry of(Collection<Class<? extends DomainEvent>> classes) {
        return new EventTypeRegistry(classes);
    }

    /**
     * The registration of {@code event}'s class.
     *
     * @throws IllegalArgumentException when the class carries no {@link EventType}
     */
    public Registration registrationOf(DomainEvent event) {
        Registration registration = byClass.get(ClassUtils.getUserClass(event));
        if (registration == null) {
            throw new IllegalArgumentException(event.getClass().getName() + " is not annotated with @EventType");
        }
        return registration;
    }

    /**
     * The registration of {@code eventClass}.
     *
     * @throws IllegalArgumentException when the class carries no {@link EventType}
     */
    public Registration registrationOf(Class<? extends DomainEvent> eventClass) {
        Registration registration = byClass.get(eventClass);
        if (registration == null) {
            throw new IllegalArgumentException(eventClass.getName() + " is not annotated with @EventType");
        }
        return registration;
    }

    /** The registration of a stored type string, empty for a type no longer known to this release. */
    public Optional<Registration> byType(String type) {
        return Optional.ofNullable(byType.get(type));
    }

    /** Every registered type string. */
    public Set<String> types() {
        return byType.keySet();
    }

    private static Registration register(Class<? extends DomainEvent> eventClass) {
        EventType annotation = eventClass.getAnnotation(EventType.class);
        if (annotation == null) {
            throw new IllegalStateException(eventClass.getName() + " is not annotated with @EventType");
        }
        if (!eventClass.isRecord()) {
            throw new IllegalStateException("Domain event " + eventClass.getName() + " must be a record");
        }
        if (!TYPE_SHAPE.matcher(annotation.value()).matches()) {
            throw new IllegalStateException("Malformed event type '" + annotation.value() + "' on " + eventClass.getName());
        }
        Set<String> sensitive = Arrays.stream(eventClass.getRecordComponents())
                .filter(component -> component.isAnnotationPresent(Sensitive.class))
                .map(RecordComponent::getName)
                .collect(Collectors.toUnmodifiableSet());
        return new Registration(annotation.value(), eventClass, annotation.retry(), sensitive);
    }

    private static Collection<Class<? extends DomainEvent>> scan() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(EventType.class));
        return scanner.findCandidateComponents(BASE_PACKAGE).stream()
                .map(BeanDefinition::getBeanClassName)
                .<Class<? extends DomainEvent>>map(EventTypeRegistry::load)
                .toList();
    }

    private static Class<? extends DomainEvent> load(String className) {
        try {
            Class<?> type = ClassUtils.forName(className, EventTypeRegistry.class.getClassLoader());
            if (!DomainEvent.class.isAssignableFrom(type)) {
                throw new IllegalStateException(className + " carries @EventType but does not implement DomainEvent");
            }
            return type.asSubclass(DomainEvent.class);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Cannot load event class " + className, e);
        }
    }
}
