package com.example.common.metrics;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class RequestMetricsFilter extends OncePerRequestFilter {

    private final RequestMetricsRecorder recorder;

    public RequestMetricsFilter(RequestMetricsRecorder recorder) {
        this.recorder = recorder;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        StatusCaptureResponseWrapper wrapper = new StatusCaptureResponseWrapper(response);
        long start = System.nanoTime();
        int status = HttpServletResponse.SC_OK;
        try {
            filterChain.doFilter(request, wrapper);
            status = wrapper.getStatus();
        } catch (Exception ex) {
            status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
            throw ex;
        } finally {
            long durationMicros = TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - start);
            recorder.record(durationMicros, status);
        }
    }

    private static class StatusCaptureResponseWrapper extends HttpServletResponseWrapper {
        StatusCaptureResponseWrapper(HttpServletResponse response) {
            super(response);
        }
    }
}
