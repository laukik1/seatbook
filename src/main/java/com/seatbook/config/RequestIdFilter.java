package com.seatbook.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class RequestIdFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger("access");

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String id = req.getHeader("X-Request-Id");
        if (id == null || id.isBlank() || id.length() > 100) id = UUID.randomUUID().toString();
        MDC.put("request_id", id);
        res.setHeader("X-Request-Id", id);
        long t0 = System.nanoTime();
        try {
            chain.doFilter(req, res);
        } finally {
            String path = req.getRequestURI();
            if (!path.equals("/metrics") && !path.equals("/healthz") && !path.equals("/readyz")) {
                log.info("{} {} -> {} in {}ms", req.getMethod(), path, res.getStatus(), (System.nanoTime() - t0) / 1_000_000);
            }
            MDC.remove("request_id");
        }
    }
}
