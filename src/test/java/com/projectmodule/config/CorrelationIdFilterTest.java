package com.projectmodule.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.projectmodule.common.context.RequestContextHeaders;
import jakarta.servlet.FilterChain;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
class CorrelationIdFilterTest {

    @Mock
    private FilterChain filterChain;

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    @DisplayName("echoes a sane correlation id so callers can match their logs to ours, and continues the chain")
    void echoesCorrelationId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestContextHeaders.CORRELATION_ID, "corr-99");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getHeader(RequestContextHeaders.CORRELATION_ID)).isEqualTo("corr-99");
        assertThat(CorrelationIdFilter.of(request)).isEqualTo("corr-99");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("generates one when the caller sends none")
    void generates() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(UUID.fromString(response.getHeader(RequestContextHeaders.CORRELATION_ID))).isNotNull();
    }

    @Test
    @DisplayName("replaces an id that is too long or has odd characters, so it cannot be used to forge log lines or headers")
    void replacesUnacceptableIds() throws Exception {
        for (String hostile : new String[] {"a".repeat(101), "id with spaces", "id\r\nSet-Cookie: x=1", "<script>"}) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader(RequestContextHeaders.CORRELATION_ID, hostile);
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, filterChain);

            assertThat(UUID.fromString(response.getHeader(RequestContextHeaders.CORRELATION_ID))).isNotNull();
        }
    }

    @Test
    @DisplayName("no longer reads identity headers at all: they are neither published nor rejected")
    void identityHeadersAreNotRead() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "not-a-uuid");
        request.addHeader("X-Org-Id", "also-not-a-uuid");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(filterChain).doFilter(request, response);
    }
}
