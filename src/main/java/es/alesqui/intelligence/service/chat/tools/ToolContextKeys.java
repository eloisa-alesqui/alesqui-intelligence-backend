package es.alesqui.intelligence.service.chat.tools;

/**
 * Keys used to store and retrieve values from the {@code ToolContext} map that is
 * passed from {@code SpringAIService} into every ReAct tool at invocation time.
 */
public final class ToolContextKeys {

    private ToolContextKeys() {}

    /** SSE sink ({@code Sinks.Many<SseEvent>}) for streaming status updates to the client. */
    public static final String SSE_SINK = "sseSink";

    /** ID of the authenticated user who triggered the chat request. */
    public static final String USER_ID = "userId";

    /** ID of the conversation associated with the current chat request. */
    public static final String CONVERSATION_ID = "conversationId";

    /** Username of the authenticated user who triggered the chat request. */
    public static final String USERNAME = "username";
}
