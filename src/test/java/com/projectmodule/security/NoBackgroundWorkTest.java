package com.projectmodule.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.RegexPatternTypeFilter;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.context.event.EventListener;
import java.util.regex.Pattern;

/**
 * docs/workeasy360-sso.md says Easy Project has no background work, so automation runs inside the request
 * that triggers it and is covered by that request's entitlement check. This keeps the sentence true: the day
 * someone adds a scheduled, asynchronous or event-driven effect, this fails and points at the document.
 */
@DisplayName("No background work")
class NoBackgroundWorkTest {

    @Test
    @DisplayName("nothing in the application is scheduled, asynchronous or driven by an event listener")
    void noBackgroundWork() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new RegexPatternTypeFilter(Pattern.compile("com\\.projectmodule\\..*")));
        List<String> offenders = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents("com.projectmodule")) {
            Class<?> type = Class.forName(definition.getBeanClassName());
            if (type.isAnnotationPresent(EnableScheduling.class) || type.isAnnotationPresent(EnableAsync.class)) {
                offenders.add(type.getName());
            }
            for (Method method : type.getDeclaredMethods()) {
                if (method.isAnnotationPresent(Scheduled.class) || method.isAnnotationPresent(Async.class)
                        || method.isAnnotationPresent(EventListener.class)
                        || method.isAnnotationPresent(TransactionalEventListener.class)) {
                    offenders.add(type.getName() + "#" + method.getName());
                }
            }
        }

        assertThat(offenders)
                .as("background work needs an entitlement re-check when it runs (docs/workeasy360-sso.md)")
                .isEmpty();
    }
}
