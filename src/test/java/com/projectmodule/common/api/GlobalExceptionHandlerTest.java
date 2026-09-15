package com.projectmodule.common.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.exception.AuthorizationException;
import com.projectmodule.common.exception.BusinessRuleViolationException;
import com.projectmodule.common.exception.ConflictException;
import com.projectmodule.common.exception.FieldViolation;
import com.projectmodule.common.exception.IntegrationUnavailableException;
import com.projectmodule.common.exception.MissingIdentityException;
import com.projectmodule.common.exception.ProjectModuleException;
import com.projectmodule.common.exception.ResourceNotFoundException;
import com.projectmodule.common.exception.ValidationException;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GlobalExceptionHandlerTest {

    private static final String CORRELATION_ID = "corr-abc";

    @Mock
    private RequestContext requestContext;

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        when(requestContext.correlationId()).thenReturn(CORRELATION_ID);
        handler = new GlobalExceptionHandler(requestContext);
    }

    static Stream<Arguments> exceptionToStatus() {
        return Stream.of(
                Arguments.of(new ResourceNotFoundException("Project", 1), HttpStatus.NOT_FOUND, "not-found"),
                Arguments.of(new ValidationException("bad"), HttpStatus.BAD_REQUEST, "validation-failed"),
                Arguments.of(new BusinessRuleViolationException("cycle"), HttpStatus.UNPROCESSABLE_ENTITY, "business-rule-violation"),
                Arguments.of(new AuthorizationException("denied"), HttpStatus.FORBIDDEN, "forbidden"),
                Arguments.of(new MissingIdentityException("none"), HttpStatus.UNAUTHORIZED, "identity-missing"),
                Arguments.of(new ConflictException("stale"), HttpStatus.CONFLICT, "conflict"),
                Arguments.of(new IntegrationUnavailableException("User Service", null), HttpStatus.SERVICE_UNAVAILABLE, "integration-unavailable"));
    }

    @ParameterizedTest(name = "{2} -> {1}")
    @MethodSource("exceptionToStatus")
    @DisplayName("maps each error type to its status and type URI")
    void mapsErrorTypes(ProjectModuleException exception, HttpStatus expected, String slug) {
        ProblemDetail problem = handler.handleProjectModuleException(exception);

        assertThat(problem.getStatus()).isEqualTo(expected.value());
        assertThat(problem.getType()).hasToString("https://errors.projectmodule/" + slug);
    }

    @Test
    @DisplayName("carries the correlation id so a response can be tied to its logs")
    void includesTraceId() {
        ProblemDetail problem = handler.handleProjectModuleException(new ConflictException("x"));

        assertThat(problem.getProperties()).containsEntry("traceId", CORRELATION_ID);
    }

    @Test
    @DisplayName("includes field-level detail when the failure is field specific")
    void includesFieldViolations() {
        ValidationException exception = new ValidationException("invalid",
                List.of(new FieldViolation("name", "must not be blank")));

        ProblemDetail problem = handler.handleProjectModuleException(exception);

        assertThat(problem.getProperties()).containsKey("errors");
        assertThat((List<?>) problem.getProperties().get("errors")).hasSize(1);
    }

    @Test
    @DisplayName("omits the errors array when there is no field detail")
    void omitsEmptyViolations() {
        ProblemDetail problem =
                handler.handleProjectModuleException(new ResourceNotFoundException("Project", 7));

        assertThat(problem.getProperties()).doesNotContainKey("errors");
    }

    @Test
    @DisplayName("never leaks an unexpected failure to the caller")
    void hidesUnexpectedFailureDetail() {
        ProblemDetail problem =
                handler.handleUnexpected(new IllegalStateException("connection string leaked"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(problem.getDetail()).isEqualTo("An unexpected error occurred");
        assertThat(problem.getDetail()).doesNotContain("connection string");
    }
}
