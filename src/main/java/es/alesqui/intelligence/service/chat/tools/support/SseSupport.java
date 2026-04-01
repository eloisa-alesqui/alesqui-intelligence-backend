package es.alesqui.intelligence.service.chat.tools.support;

import org.springframework.ai.chat.model.ToolContext;

import es.alesqui.intelligence.dto.chat.response.SseEvent;
import es.alesqui.intelligence.service.chat.tools.ToolContextKeys;
import reactor.core.publisher.Sinks;

/**
 * Utility class for safely retrieving the Server-Sent Events sink from a tool context.
 * 
 * Tools may optionally emit progress updates through an {@code SseEvent} sink if
 * the caller provided one in the {@code ToolContext}. This helper centralizes the
 * extraction logic and guarantees a null-safe, type-safe access pattern.
 * 
 * Characteristics
 * - Non-throwing: returns null when the sink is not present or has an unexpected type.
 * - Type-checked: validates the value is a {@code Sinks.Many<SseEvent>} before casting.
 * - Null-safe: accepts a null {@code ToolContext} and returns null.
 * 
 * Typical usage
 * {@code
 * Sinks.Many<SseEvent> sink = SseSupport.getSinkFromContext(toolContext);
 * if (sink != null) sink.tryEmitNext(SseEvent.status("Working..."));
 * }
 */
public final class SseSupport {

    private SseSupport() {}

    /**
     * Attempts to extract the {@code Sinks.Many<SseEvent>} stored under the key
     * {@code "sseSink"} within the provided {@code ToolContext}. If the context
     * is null, contains no map, the key is absent, or the value is not of the
     * expected type, this method returns null.
     * 
     * The method does not throw when a type mismatch occurs; it catches the
     * {@code ClassCastException} and returns null instead.
     * 
     * @param toolContext the tool context that may contain the sink under key "sseSink"
     * @return the sink for publishing {@code SseEvent} updates, or null if unavailable
     */
    @SuppressWarnings("unchecked")
    public static Sinks.Many<SseEvent> getSinkFromContext(ToolContext toolContext) {
        if (toolContext != null && toolContext.getContext() != null) {
            Object sinkObj = toolContext.getContext().get(ToolContextKeys.SSE_SINK);
            if (sinkObj instanceof Sinks.Many) {
                try {
                    return (Sinks.Many<SseEvent>) sinkObj;
                } catch (ClassCastException e) {
                    // ignore wrong type
                }
            }
        }
        return null;
    }

    /**
     * Emits a status {@code SseEvent} to the sink in the given tool context,
     * if one is present. No-op when the context contains no sink.
     *
     * @param toolContext the tool context that may contain an SSE sink under key "sseSink"
     * @param message the status message to emit
     */
    public static void emitStatus(ToolContext toolContext, String message) {
        Sinks.Many<SseEvent> sink = getSinkFromContext(toolContext);
        if (sink != null) {
            sink.tryEmitNext(SseEvent.status(message));
        }
    }
}
