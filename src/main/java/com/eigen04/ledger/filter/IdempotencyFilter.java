package com.eigen04.ledger.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.eigen04.ledger.dto.response.ApiErrorResponse;
import com.eigen04.ledger.entity.IdempotencyRecord;
import com.eigen04.ledger.enums.IdempotencyStatus;
import com.eigen04.ledger.repository.IdempotencyRecordRepository;
import com.eigen04.ledger.util.HashUtil;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class IdempotencyFilter implements Filter {

    private static final long LEASE_DURATION_SECONDS = 30L;
    private static final int MAX_SCOPED_KEY_LENGTH = 255;

    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
                         FilterChain filterChain) throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String rawKey = request.getHeader("Idempotency-Key");
        if (rawKey == null || rawKey.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        String scopedKey = request.getMethod().toUpperCase() + ":" + request.getRequestURI() + ":" + rawKey.trim();
        if (scopedKey.length() > MAX_SCOPED_KEY_LENGTH) {
            sendError(response, HttpStatus.BAD_REQUEST,
                    "Idempotency-Key exceeds maximum supported length", request.getRequestURI());
            return;
        }

        CachedBodyHttpServletRequest wrappedRequest;
        try {
            wrappedRequest = new CachedBodyHttpServletRequest(request);
        } catch (CachedBodyHttpServletRequest.PayloadTooLargeException ex) {
            sendError(response, HttpStatus.PAYLOAD_TOO_LARGE, ex.getMessage(), request.getRequestURI());
            return;
        }

        String requestHash = HashUtil.sha256(wrappedRequest.getBodyAsString());
        // Truncate to microseconds to match PostgreSQL TIMESTAMPTZ precision for exact lease fencing.
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Instant leaseExpiry = now.plusSeconds(LEASE_DURATION_SECONDS);

        if (!acquireOrReplay(scopedKey, requestHash, now, leaseExpiry, wrappedRequest, response)) {
            return;
        }

        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);

        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse);

            int responseStatus = wrappedResponse.getStatus();
            String responseBody = new String(wrappedResponse.getContentAsByteArray(), StandardCharsets.UTF_8);

            Instant completionTime = Instant.now().truncatedTo(ChronoUnit.MICROS);
            if (responseStatus >= 500) {
                int updated = idempotencyRecordRepository.markFailedIfLeaseHeld(
                        scopedKey, leaseExpiry, completionTime);
                if (updated == 0) {
                    log.warn("Lost idempotency lease before marking FAILED for key {}", scopedKey);
                }
            } else {
                int updated = idempotencyRecordRepository.markCompletedIfLeaseHeld(
                        scopedKey, leaseExpiry, responseStatus, responseBody, completionTime);
                if (updated == 0) {
                    log.warn("Lost idempotency lease before marking COMPLETED for key {}", scopedKey);
                }
            }

            wrappedResponse.copyBodyToResponse();
        } catch (Exception e) {
            int updated = idempotencyRecordRepository.markFailedIfLeaseHeld(
                    scopedKey, leaseExpiry, Instant.now().truncatedTo(ChronoUnit.MICROS));
            if (updated == 0) {
                log.warn("Lost idempotency lease before marking FAILED on exception for key {}", scopedKey);
            }
            throw e;
        }
    }

    private boolean acquireOrReplay(String scopedKey,
                                    String requestHash,
                                    Instant now,
                                    Instant leaseExpiry,
                                    HttpServletRequest request,
                                    HttpServletResponse response) throws IOException {
        Optional<IdempotencyRecord> existing = idempotencyRecordRepository.findByIdempotencyKey(scopedKey);
        if (existing.isPresent()) {
            return handleExistingRecord(existing.get(), scopedKey, requestHash, now, leaseExpiry, request, response);
        }

        try {
            idempotencyRecordRepository.save(IdempotencyRecord.builder()
                    .idempotencyKey(scopedKey)
                    .requestHash(requestHash)
                    .status(IdempotencyStatus.IN_PROGRESS)
                    .lockedUntil(leaseExpiry)
                    .build());
            return true;
        } catch (DataIntegrityViolationException e) {
            Optional<IdempotencyRecord> raceRecord = idempotencyRecordRepository.findByIdempotencyKey(scopedKey);
            if (raceRecord.isPresent() && !raceRecord.get().getRequestHash().equals(requestHash)) {
                sendError(response, HttpStatus.UNPROCESSABLE_ENTITY,
                        "Idempotency key reused with a different request payload", request.getRequestURI());
                return false;
            }
            sendError(response, HttpStatus.CONFLICT,
                    "Request with this idempotency key is currently being processed", request.getRequestURI());
            return false;
        }
    }

    private boolean handleExistingRecord(IdempotencyRecord record,
                                         String scopedKey,
                                         String requestHash,
                                         Instant now,
                                         Instant leaseExpiry,
                                         HttpServletRequest request,
                                         HttpServletResponse response) throws IOException {
        if (!record.getRequestHash().equals(requestHash)) {
            log.warn("Payload mismatch for idempotency key {}", scopedKey);
            sendError(response, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Idempotency key reused with a different request payload", request.getRequestURI());
            return false;
        }

        if (record.getStatus() == IdempotencyStatus.COMPLETED) {
            response.setStatus(record.getResponseStatus());
            response.setContentType("application/json");
            if (record.getResponseBody() != null) {
                response.getWriter().write(record.getResponseBody());
            }
            return false;
        }

        boolean leaseExpired = record.getStatus() == IdempotencyStatus.IN_PROGRESS
                && record.getLockedUntil() != null
                && record.getLockedUntil().isBefore(now);

        if (record.getStatus() == IdempotencyStatus.FAILED || leaseExpired) {
            int reclaimed = idempotencyRecordRepository.reclaimLease(
                    scopedKey, requestHash, leaseExpiry, now);
            if (reclaimed > 0) {
                return true;
            }
        }

        sendError(response, HttpStatus.CONFLICT,
                "Request with this idempotency key is currently being processed", request.getRequestURI());
        return false;
    }

    private void sendError(HttpServletResponse response, HttpStatus status,
                           String message, String path) throws IOException {
        ApiErrorResponse error = ApiErrorResponse.builder()
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(path)
                .timestamp(Instant.now())
                .build();
        response.setStatus(status.value());
        response.setContentType("application/json");
        response.getWriter().write(objectMapper.writeValueAsString(error));
    }
}
