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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class IdempotencyFilter implements Filter {

    private static final long LEASE_DURATION_SECONDS = 30L;

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

        String idempotencyKey = request.getHeader("Idempotency-Key");
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        CachedBodyHttpServletRequest wrappedRequest = new CachedBodyHttpServletRequest(request);
        String requestHash = HashUtil.sha256(wrappedRequest.getBodyAsString());
        Instant now = Instant.now();
        Instant leaseExpiry = now.plusSeconds(LEASE_DURATION_SECONDS);

        if (!acquireOrReplay(idempotencyKey, requestHash, now, leaseExpiry, wrappedRequest, response)) {
            return;
        }

        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);

        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse);

            int responseStatus = wrappedResponse.getStatus();
            String responseBody = new String(wrappedResponse.getContentAsByteArray(), StandardCharsets.UTF_8);

            if (responseStatus >= 500) {
                markFailed(idempotencyKey);
            } else {
                markCompleted(idempotencyKey, responseStatus, responseBody);
            }

            wrappedResponse.copyBodyToResponse();
        } catch (Exception e) {
            markFailed(idempotencyKey);
            throw e;
        }
    }

    private boolean acquireOrReplay(String idempotencyKey,
                                    String requestHash,
                                    Instant now,
                                    Instant leaseExpiry,
                                    HttpServletRequest request,
                                    HttpServletResponse response) throws IOException {
        Optional<IdempotencyRecord> existing = idempotencyRecordRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return handleExistingRecord(existing.get(), idempotencyKey, requestHash, now, leaseExpiry, request, response);
        }

        try {
            idempotencyRecordRepository.save(IdempotencyRecord.builder()
                    .idempotencyKey(idempotencyKey)
                    .requestHash(requestHash)
                    .status(IdempotencyStatus.IN_PROGRESS)
                    .lockedUntil(leaseExpiry)
                    .build());
            return true;
        } catch (DataIntegrityViolationException e) {
            Optional<IdempotencyRecord> raceRecord = idempotencyRecordRepository.findByIdempotencyKey(idempotencyKey);
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
                                         String idempotencyKey,
                                         String requestHash,
                                         Instant now,
                                         Instant leaseExpiry,
                                         HttpServletRequest request,
                                         HttpServletResponse response) throws IOException {
        if (!record.getRequestHash().equals(requestHash)) {
            log.warn("Payload mismatch for idempotency key {}", idempotencyKey);
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
                    idempotencyKey, requestHash, leaseExpiry, now);
            if (reclaimed > 0) {
                return true;
            }
        }

        sendError(response, HttpStatus.CONFLICT,
                "Request with this idempotency key is currently being processed", request.getRequestURI());
        return false;
    }

    private void markCompleted(String idempotencyKey, int responseStatus, String responseBody) {
        idempotencyRecordRepository.findByIdempotencyKey(idempotencyKey)
                .ifPresent(record -> {
                    record.setStatus(IdempotencyStatus.COMPLETED);
                    record.setResponseStatus(responseStatus);
                    record.setResponseBody(responseBody);
                    record.setLockedUntil(null);
                    idempotencyRecordRepository.save(record);
                });
    }

    private void markFailed(String idempotencyKey) {
        idempotencyRecordRepository.findByIdempotencyKey(idempotencyKey)
                .ifPresent(record -> {
                    record.setStatus(IdempotencyStatus.FAILED);
                    record.setLockedUntil(null);
                    idempotencyRecordRepository.save(record);
                });
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
