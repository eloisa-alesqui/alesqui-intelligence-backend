package es.alesqui.intelligence.dto.chat.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A Data Transfer Object (DTO) for Server-Sent Events (SSE).
 * This class standardizes the data structure for messages streamed from the
 * backend to the frontend during an asynchronous chat process. It allows the
 * client to distinguish between progress updates, the final result, and errors.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SseEvent {

    /**
     * The type of the event, which categorizes the message.
     * See the EventType enum for possible values.
     */
    private EventType type;

    /**
     * The data payload of the event.
     * The content varies depending on the event type, such as a String for
     * status messages or a ChatResponse object for the final answer.
     */
    private Object payload;

    /**
     * Defines the distinct types of events that can be streamed.
     */
    public enum EventType {
        /**
         * A progress update message, typically a simple string.
         */
        STATUS,
        /**
         * The final, complete chat response object.
         */
        FINAL_RESPONSE,
        /**
         * An error message, typically a string.
         */
        ERROR
    }

    /**
     * A static factory method to create a STATUS event.
     *
     * @param message The progress message to be sent.
     * @return A new SseEvent instance with the STATUS type.
     */
    public static SseEvent status(String message) {
        return new SseEvent(EventType.STATUS, message);
    }

    /**
     * A static factory method to create a FINAL_RESPONSE event.
     *
     * @param response The final ChatResponse object.
     * @return A new SseEvent instance with the FINAL_RESPONSE type.
     */
    public static SseEvent finalResponse(ChatResponse response) {
        return new SseEvent(EventType.FINAL_RESPONSE, response);
    }

    /**
     * A static factory method to create an ERROR event.
     *
     * @param errorMessage The error message to be sent.
     * @return A new SseEvent instance with the ERROR type.
     */
    public static SseEvent error(String errorMessage) {
        return new SseEvent(EventType.ERROR, errorMessage);
    }
}
