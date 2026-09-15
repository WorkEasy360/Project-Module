package com.projectmodule.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.context.RequestContextFactory;
import com.projectmodule.common.context.RequestContextHeaders;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
class RequestContextFilterTest {

    private static final String USER = "11111111-1111-1111-1111-111111111111";

    @Mock
    private FilterChain filterChain;

    private final RequestContextFilter filter =
            new RequestContextFilter(new RequestContextFactory(), new ObjectMapper());

    @Test
    @DisplayName("publishes the context and continues the chain")
    void publishesContext() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestContextHeaders.USER_ID, USER);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        Object attribute = request.getAttribute(RequestContextFilter.ATTRIBUTE);
        assertThat(attribute).isInstanceOf(RequestContext.class);
        assertThat(((RequestContext) attribute).userId()).isPresent();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("echoes the correlation id so callers can match their logs to ours")
    void echoesCorrelationId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestContextHeaders.CORRELATION_ID, "corr-99");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getHeader(RequestContextHeaders.CORRELATION_ID)).isEqualTo("corr-99");
    }

    @Test
    @DisplayName("passes a request with no identity through untouched")
    void allowsAnonymousRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
    }

    @Test
    @DisplayName("answers a malformed identity header itself, since no advice runs this early")
    void rejectsMalformedHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestContextHeaders.USER_ID, "not-a-uuid");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(response.getContentAsString()).contains("validation-failed");
        verify(filterChain, never()).doFilter(request, response);
    }
}
