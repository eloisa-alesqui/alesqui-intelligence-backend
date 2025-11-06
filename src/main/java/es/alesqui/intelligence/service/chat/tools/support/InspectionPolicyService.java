package es.alesqui.intelligence.service.chat.tools.support;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

/**
 * Tracks endpoint inspections per conversation to enforce an
 * "inspect before execute" policy. This helps ensure the model
 * reviews the latest contract before calling an API endpoint.
 */
@Service
public class InspectionPolicyService {

    private final ConcurrentHashMap<String, ConcurrentHashMap<String, Instant>> inspections = new ConcurrentHashMap<>();

    /**
     * Records that a given operation has been inspected for a conversation at the current time.
     */
    public void recordInspection(String conversationId, String apiName, String operationId) {
        if (conversationId == null || apiName == null || operationId == null) {
            return;
        }
        String key = key(apiName, operationId);
        inspections.computeIfAbsent(conversationId, cid -> new ConcurrentHashMap<>())
                   .put(key, Instant.now());
    }

    /**
     * Checks whether the operation was inspected recently within the provided TTL.
     * Returns false if conversationId is null or no inspection was found.
     */
    public boolean wasInspectedRecently(String conversationId, String apiName, String operationId, Duration ttl) {
        if (conversationId == null || apiName == null || operationId == null) {
            return false;
        }
        Map<String, Instant> map = inspections.get(conversationId);
        if (map == null) return false;
        Instant ts = map.get(key(apiName, operationId));
        if (ts == null) return false;
        return ts.plus(ttl).isAfter(Instant.now());
    }

    private String key(String apiName, String operationId) {
        return apiName + "|" + operationId;
    }
}
