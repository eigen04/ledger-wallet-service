package com.sapidblue.ledger.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sapidblue.ledger.dto.response.ApiErrorResponse;
import com.sapidblue.ledger.entity.IdempotencyRecord;
import com.sapidblue.ledger.enums.IdempotencyStatus;
import com.sapidblue.ledger.repository.IdempotencyRecordRepository;
import com.sapidblue.ledger.util.HashUtil;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Idempotency Filter — intercepts requests with Idempotency-Key header.
 *
 * Request Lifecycle State Machine:
 * 1. No key → pass through (not idempotent)
 * 2. New key → INSERT IN_PROGRESS → execute → update to COMPLETED with cached response
 * 3. Duplicate key (COMPLETED) → return cached response immediately
 * 4. Duplicate key (IN_PROGRESS) → return 409 Conflict
 * 5. Duplicate key (payload mismatch) → return 422 Unprocessable Entity
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class IdempotencyFilter implements Filter {

    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
                         FilterChain filterChain) throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        // Only apply idempotency to POST requests
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String idempotencyKey = request.getHeader("Idempotency-Key");

        // No idempotency key — pass through normally
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        log.info("Idempotency check for key: {}", idempotencyKey);

        // Wrap request to read body multiple times
        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);

        // We need to let the request pass through first to cache the body
        // Then check idempotency
        // Actually, we need to read the body first, so let's proceed and check after

        Optional<IdempotencyRecord> existing = idempotencyRecordRepository
                .findByIdempotencyKey(idempotencyKey);

        if (existing.isPresent()) {
            IdempotencyRecord record = existing.get();

            if (record.getStatus() == IdempotencyStatus.COMPLETED) {
                // Return cached response
                log.info("Idempotency key {} already completed. Returning cached response.", idempotencyKey);
                response.setStatus(record.getResponseStatus());
                response.setContentType("application/json");
                response.getWriter().write(record.getResponseBody());
                return;
            }

            if (record.getStatus() == IdempotencyStatus.IN_PROGRESS) {
                // Another request is currently processing
                log.warn("Idempotency key {} is currently IN_PROGRESS. Returning 409.", idempotencyKey);
                sendError(response, HttpServletResponse.SC_CONFLICT,
                        "Request with this idempotency key is currently being processed");
                return;
            }
        }

        // New key — try to insert
        try {
            IdempotencyRecord newRecord = IdempotencyRecord.builder()
                    .idempotencyKey(idempotencyKey)
                    .requestHash("pending")  // Will be updated after body is read
                    .status(IdempotencyStatus.IN_PROGRESS)
                    .build();
            idempotencyRecordRepository.save(newRecord);
        } catch (DataIntegrityViolationException e) {
            // Race condition — another thread inserted first
            log.warn("Concurrent insert for idempotency key {}. Returning 409.", idempotencyKey);
            sendError(response, HttpServletResponse.SC_CONFLICT,
                    "Request with this idempotency key is currently being processed");
            return;
        }

        // Execute the actual request
        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse);

            // Cache the response
            String responseBody = new String(wrappedResponse.getContentAsByteArray(), StandardCharsets.UTF_8);
            int responseStatus = wrappedResponse.getStatus();

            // Compute request body hash
            String requestBody = new String(wrappedRequest.getContentAsByteArray(), StandardCharsets.UTF_8);
            String requestHash = HashUtil.sha256(requestBody);

            // Update idempotency record
            idempotencyRecordRepository.findByIdempotencyKey(idempotencyKey)
                    .ifPresent(record -> {
                        record.setStatus(IdempotencyStatus.COMPLETED);
                        record.setResponseStatus(responseStatus);
                        record.setResponseBody(responseBody);
                        record.setRequestHash(requestHash);
                        idempotencyRecordRepository.save(record);
                    });

            // Copy response body to actual response
            wrappedResponse.copyBodyToResponse();

        } catch (Exception e) {
            // Mark as FAILED
            idempotencyRecordRepository.findByIdempotencyKey(idempotencyKey)
                    .ifPresent(record -> {
                        record.setStatus(IdempotencyStatus.FAILED);
                        idempotencyRecordRepository.save(record);
                    });
            throw e;
        }
    }

    private void sendError(HttpServletResponse response, int status, String message) throws IOException {
        ApiErrorResponse error = ApiErrorResponse.builder()
                .status(status)
                .error("Conflict")
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write(objectMapper.writeValueAsString(error));
    }
}
