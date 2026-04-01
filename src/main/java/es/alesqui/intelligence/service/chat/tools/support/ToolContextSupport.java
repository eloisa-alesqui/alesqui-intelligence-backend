package es.alesqui.intelligence.service.chat.tools.support;

import org.springframework.ai.chat.model.ToolContext;

import es.alesqui.intelligence.service.chat.tools.ToolContextKeys;

/**
 * ToolContext-related helpers shared across tools.
 *
 * All methods are static and side-effect free. The class is not intended to be
 * instantiated.
 */
public final class ToolContextSupport {

    private ToolContextSupport() {}

    /**
     * Extracts the authenticated user ID from the given {@link ToolContext}.
     *
     * @param toolContext context passed by Spring AI at tool invocation time; may be {@code null}
     * @return the user ID string, or {@code null} if the context or the key is absent
     */
    public static String getUserId(ToolContext toolContext) {
        return toolContext != null && toolContext.getContext() != null
                ? (String) toolContext.getContext().get(ToolContextKeys.USER_ID)
                : null;
    }

    /**
     * Extracts the authenticated username from the given {@link ToolContext}.
     *
     * @param toolContext context passed by Spring AI at tool invocation time; may be {@code null}
     * @return the username string, or {@code null} if the context or the key is absent
     */
    public static String getUsername(ToolContext toolContext) {
        return toolContext != null && toolContext.getContext() != null
                ? (String) toolContext.getContext().get(ToolContextKeys.USERNAME)
                : null;
    }
}
