package es.alesqui.intelligence.service.conversation;

import es.alesqui.intelligence.model.conversation.ConversationRecord;
import es.alesqui.intelligence.model.conversation.ConversationStatus;
import es.alesqui.intelligence.repository.ConversationRecordRepository;
import es.alesqui.intelligence.service.access.GroupMembershipService;
import es.alesqui.intelligence.service.identity.UserService;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for ConversationService.exportConversationsForUser().
 * Uses Mockito to mock the repository layer — no Spring context needed.
 *
 * The implementation delegates to getHistoryForUser() (MongoDB aggregation) for
 * summaries, then calls findByConversationIdOrderByTimestampAsc() per conversation.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ConversationService - exportConversationsForUser")
class ConversationServiceExportTest {

    @Mock
    private ConversationRecordRepository repository;

    @Mock
    private GroupMembershipService groupMembershipService;

    @Mock
    private UserService userService;

    @Mock
    private ReactiveMongoTemplate mongoTemplate;

    @InjectMocks
    private ConversationService conversationService;

    private static final String USERNAME = "alice@example.com";
    private static final String CONV_ID_1 = "conv-001";
    private static final String CONV_ID_2 = "conv-002";

    private Instant now;
    private Instant oneHourAgo;
    private Instant twoHoursAgo;

    @BeforeEach
    void setUp() {
        now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        oneHourAgo = now.minus(1, ChronoUnit.HOURS);
        twoHoursAgo = now.minus(2, ChronoUnit.HOURS);
    }

    // ---------------------------------------------------------------------------
    // Helper builders
    // ---------------------------------------------------------------------------

    private ConversationRecord record(String id, String convId, String username,
                                      String prompt, Instant ts, ConversationStatus status) {
        return ConversationRecord.builder()
                .id(id)
                .conversationId(convId)
                .username(username)
                .userPrompt(prompt)
                .responseText("Response for: " + prompt)
                .timestamp(ts)
                .status(status)
                .build();
    }

    private ConversationRecord record(String id, String convId, String username, String prompt, Instant ts) {
        return record(id, convId, username, prompt, ts, ConversationStatus.SUCCESS);
    }

    /** Builds a summary Document as returned by the MongoDB $group aggregation stage. */
    private Document summaryDoc(String convId, String title, Instant lastUpdated) {
        return new Document("_id", convId)
                .append("title", title)
                .append("lastUpdated", Date.from(lastUpdated));
    }

    // ---------------------------------------------------------------------------
    // Happy-path scenarios
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("Should export two conversations with correct metadata and message counts")
        void shouldExportTwoConversations() {
            ConversationRecord r1a = record("r1a", CONV_ID_1, USERNAME, "First question", twoHoursAgo);
            ConversationRecord r1b = record("r1b", CONV_ID_1, USERNAME, "Follow-up question", oneHourAgo);
            ConversationRecord r2a = record("r2a", CONV_ID_2, USERNAME, "Other question", now);

            // Aggregate returns summaries sorted DESC by lastUpdated
            when(mongoTemplate.aggregate(any(Aggregation.class), eq("conversations"), eq(Document.class)))
                    .thenReturn(Flux.just(
                            summaryDoc(CONV_ID_2, "Other question", now),
                            summaryDoc(CONV_ID_1, "First question", oneHourAgo)
                    ));
            when(repository.findByConversationIdOrderByTimestampAsc(CONV_ID_2))
                    .thenReturn(Flux.just(r2a));
            when(repository.findByConversationIdOrderByTimestampAsc(CONV_ID_1))
                    .thenReturn(Flux.just(r1a, r1b));

            StepVerifier.create(conversationService.exportConversationsForUser(USERNAME))
                    .assertNext(export -> {
                        assertThat(export.getUsername()).isEqualTo(USERNAME);
                        assertThat(export.getExportedAt()).isNotNull();
                        assertThat(export.getTotalConversations()).isEqualTo(2);
                        assertThat(export.getConversations()).hasSize(2);

                        var conv1 = export.getConversations().stream()
                                .filter(c -> CONV_ID_1.equals(c.getConversationId()))
                                .findFirst().orElseThrow();
                        assertThat(conv1.getTitle()).isEqualTo("First question");
                        assertThat(conv1.getLastUpdated()).isEqualTo(oneHourAgo);
                        assertThat(conv1.getMessages()).hasSize(2);
                        // Messages must be sorted chronologically (asc)
                        assertThat(conv1.getMessages().get(0).getUserPrompt()).isEqualTo("First question");
                        assertThat(conv1.getMessages().get(1).getUserPrompt()).isEqualTo("Follow-up question");

                        var conv2 = export.getConversations().stream()
                                .filter(c -> CONV_ID_2.equals(c.getConversationId()))
                                .findFirst().orElseThrow();
                        assertThat(conv2.getTitle()).isEqualTo("Other question");
                        assertThat(conv2.getMessages()).hasSize(1);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should return empty conversations list when user has no history")
        void shouldReturnEmptyWhenNoHistory() {
            when(mongoTemplate.aggregate(any(Aggregation.class), eq("conversations"), eq(Document.class)))
                    .thenReturn(Flux.empty());

            StepVerifier.create(conversationService.exportConversationsForUser(USERNAME))
                    .assertNext(export -> {
                        assertThat(export.getUsername()).isEqualTo(USERNAME);
                        assertThat(export.getExportedAt()).isNotNull();
                        assertThat(export.getTotalConversations()).isZero();
                        assertThat(export.getConversations()).isEmpty();
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should use the first chronological prompt as the conversation title")
        void shouldUseFirstChronologicalPromptAsTitle() {
            ConversationRecord first  = record("r1", CONV_ID_1, USERNAME, "Very first question", twoHoursAgo);
            ConversationRecord second = record("r2", CONV_ID_1, USERNAME, "Second question", oneHourAgo);

            // MongoDB $group picks the title; test verifies it flows through correctly
            when(mongoTemplate.aggregate(any(Aggregation.class), eq("conversations"), eq(Document.class)))
                    .thenReturn(Flux.just(summaryDoc(CONV_ID_1, "Very first question", oneHourAgo)));
            when(repository.findByConversationIdOrderByTimestampAsc(CONV_ID_1))
                    .thenReturn(Flux.just(first, second));

            StepVerifier.create(conversationService.exportConversationsForUser(USERNAME))
                    .assertNext(export -> assertThat(export.getConversations().get(0).getTitle())
                            .isEqualTo("Very first question"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should set lastUpdated to the most recent message timestamp")
        void shouldSetLastUpdatedToMostRecentTimestamp() {
            ConversationRecord early = record("r1", CONV_ID_1, USERNAME, "Q1", twoHoursAgo);
            ConversationRecord late  = record("r2", CONV_ID_1, USERNAME, "Q2", oneHourAgo);

            when(mongoTemplate.aggregate(any(Aggregation.class), eq("conversations"), eq(Document.class)))
                    .thenReturn(Flux.just(summaryDoc(CONV_ID_1, "Q1", oneHourAgo)));
            when(repository.findByConversationIdOrderByTimestampAsc(CONV_ID_1))
                    .thenReturn(Flux.just(early, late));

            StepVerifier.create(conversationService.exportConversationsForUser(USERNAME))
                    .assertNext(export -> assertThat(export.getConversations().get(0).getLastUpdated())
                            .isEqualTo(oneHourAgo))
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should mark error-status records as isError=true in messages")
        void shouldMarkErrorRecordsInMessages() {
            ConversationRecord errorRecord = record("r1", CONV_ID_1, USERNAME, "Question",
                    now, ConversationStatus.ERROR_PROCESSING);

            when(mongoTemplate.aggregate(any(Aggregation.class), eq("conversations"), eq(Document.class)))
                    .thenReturn(Flux.just(summaryDoc(CONV_ID_1, "Question", now)));
            when(repository.findByConversationIdOrderByTimestampAsc(CONV_ID_1))
                    .thenReturn(Flux.just(errorRecord));

            StepVerifier.create(conversationService.exportConversationsForUser(USERNAME))
                    .assertNext(export -> assertThat(
                            export.getConversations().get(0).getMessages().get(0).isError()).isTrue())
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should mark SUCCESS records as isError=false in messages")
        void shouldMarkSuccessRecordsAsNotError() {
            ConversationRecord okRecord = record("r1", CONV_ID_1, USERNAME, "Question",
                    now, ConversationStatus.SUCCESS);

            when(mongoTemplate.aggregate(any(Aggregation.class), eq("conversations"), eq(Document.class)))
                    .thenReturn(Flux.just(summaryDoc(CONV_ID_1, "Question", now)));
            when(repository.findByConversationIdOrderByTimestampAsc(CONV_ID_1))
                    .thenReturn(Flux.just(okRecord));

            StepVerifier.create(conversationService.exportConversationsForUser(USERNAME))
                    .assertNext(export -> assertThat(
                            export.getConversations().get(0).getMessages().get(0).isError()).isFalse())
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should include userFeedbackComment in exported messages when present")
        void shouldIncludeFeedbackCommentInMessages() {
            ConversationRecord reported = ConversationRecord.builder()
                    .id("r1")
                    .conversationId(CONV_ID_1)
                    .username(USERNAME)
                    .userPrompt("Question")
                    .responseText("Answer")
                    .timestamp(now)
                    .status(ConversationStatus.REPORTED_BY_USER)
                    .userFeedbackComment("This answer was wrong")
                    .build();

            when(mongoTemplate.aggregate(any(Aggregation.class), eq("conversations"), eq(Document.class)))
                    .thenReturn(Flux.just(summaryDoc(CONV_ID_1, "Question", now)));
            when(repository.findByConversationIdOrderByTimestampAsc(CONV_ID_1))
                    .thenReturn(Flux.just(reported));

            StepVerifier.create(conversationService.exportConversationsForUser(USERNAME))
                    .assertNext(export -> assertThat(
                            export.getConversations().get(0).getMessages().get(0).getUserFeedbackComment())
                            .isEqualTo("This answer was wrong"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should sort conversations by lastUpdated descending")
        void shouldSortConversationsByLastUpdatedDesc() {
            ConversationRecord old    = record("r1", CONV_ID_1, USERNAME, "Old question", twoHoursAgo);
            ConversationRecord recent = record("r2", CONV_ID_2, USERNAME, "Recent question", now);

            // Aggregate already returns in DESC order; concatMap preserves that order
            when(mongoTemplate.aggregate(any(Aggregation.class), eq("conversations"), eq(Document.class)))
                    .thenReturn(Flux.just(
                            summaryDoc(CONV_ID_2, "Recent question", now),
                            summaryDoc(CONV_ID_1, "Old question", twoHoursAgo)
                    ));
            when(repository.findByConversationIdOrderByTimestampAsc(CONV_ID_2))
                    .thenReturn(Flux.just(recent));
            when(repository.findByConversationIdOrderByTimestampAsc(CONV_ID_1))
                    .thenReturn(Flux.just(old));

            StepVerifier.create(conversationService.exportConversationsForUser(USERNAME))
                    .assertNext(export -> {
                        assertThat(export.getConversations().get(0).getConversationId()).isEqualTo(CONV_ID_2);
                        assertThat(export.getConversations().get(1).getConversationId()).isEqualTo(CONV_ID_1);
                    })
                    .verifyComplete();
        }
    }

    // ---------------------------------------------------------------------------
    // Ownership enforcement
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("Ownership enforcement")
    class OwnershipEnforcement {

        @Test
        @DisplayName("Should only export records returned for the queried username")
        void shouldOnlyIncludeRecordsForQueriedUser() {
            ConversationRecord r1 = record("r1", CONV_ID_1, USERNAME, "My question", twoHoursAgo);
            ConversationRecord r2 = record("r2", CONV_ID_1, USERNAME, "My follow-up", oneHourAgo);

            when(mongoTemplate.aggregate(any(Aggregation.class), eq("conversations"), eq(Document.class)))
                    .thenReturn(Flux.just(summaryDoc(CONV_ID_1, "My question", oneHourAgo)));
            when(repository.findByConversationIdOrderByTimestampAsc(CONV_ID_1))
                    .thenReturn(Flux.just(r1, r2));

            StepVerifier.create(conversationService.exportConversationsForUser(USERNAME))
                    .assertNext(export -> {
                        assertThat(export.getTotalConversations()).isEqualTo(1);
                        var messages = export.getConversations().get(0).getMessages();
                        assertThat(messages).hasSize(2);
                        assertThat(messages).allMatch(m -> m.getUserPrompt().startsWith("My"));
                    })
                    .verifyComplete();
        }
    }
}
