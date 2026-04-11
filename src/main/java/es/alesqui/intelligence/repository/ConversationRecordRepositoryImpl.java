package es.alesqui.intelligence.repository;

import es.alesqui.intelligence.dto.conversation.ConversationSummaryDTO;
import es.alesqui.intelligence.dto.conversation.LastConversationInfo;
import es.alesqui.intelligence.dto.conversation.TicketStatsDTO;
import es.alesqui.intelligence.model.conversation.ConversationStatus;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

@RequiredArgsConstructor
public class ConversationRecordRepositoryImpl implements ConversationRecordRepositoryCustom {

    private final ReactiveMongoTemplate mongoTemplate;

    @Override
    public Flux<ConversationSummaryDTO> findConversationSummariesByUsername(String username) {
        return mongoTemplate.aggregate(
            Aggregation.newAggregation(
                Aggregation.match(Criteria.where("username").is(username)),
                Aggregation.sort(Sort.by(Sort.Direction.ASC, "timestamp")),
                Aggregation.group("conversationId")
                    .first("userPrompt").as("title")
                    .max("timestamp").as("lastUpdated"),
                Aggregation.sort(Sort.by(Sort.Direction.DESC, "lastUpdated"))
            ),
            "conversations",
            Document.class
        )
        .map(doc -> new ConversationSummaryDTO(
            doc.getString("_id"),
            doc.getString("title"),
            doc.getDate("lastUpdated").toInstant()
        ));
    }

    @Override
    public Mono<Long> countDistinctConversationsByUsername(String username) {
        return mongoTemplate.aggregate(
            Aggregation.newAggregation(
                Aggregation.match(Criteria.where("username").is(username)),
                Aggregation.group("conversationId"),
                Aggregation.count().as("count")
            ),
            "conversations",
            Document.class
        )
        .next()
        .<Long>map(doc -> ((Number) doc.get("count")).longValue())
        .defaultIfEmpty(0L);
    }

    @Override
    public Mono<Long> countDistinctConversationsByUsernameAndTimestampAfter(String username, Instant since) {
        return mongoTemplate.aggregate(
            Aggregation.newAggregation(
                Aggregation.match(Criteria.where("username").is(username).and("timestamp").gte(since)),
                Aggregation.group("conversationId"),
                Aggregation.count().as("count")
            ),
            "conversations",
            Document.class
        )
        .next()
        .<Long>map(doc -> ((Number) doc.get("count")).longValue())
        .defaultIfEmpty(0L);
    }

    @Override
    public Mono<TicketStatsDTO> countTicketsByStatusSince(Instant since) {
        List<String> ticketStatuses = List.of(
            ConversationStatus.ERROR_PROCESSING.name(),
            ConversationStatus.REPORTED_BY_USER.name(),
            ConversationStatus.UNDER_REVIEW.name(),
            ConversationStatus.RESOLVED.name()
        );

        return mongoTemplate.aggregate(
            Aggregation.newAggregation(
                Aggregation.match(
                    Criteria.where("timestamp").gte(since)
                        .and("status").in(ticketStatuses)
                ),
                Aggregation.group("status").count().as("count")
            ),
            "conversations",
            Document.class
        )
        .collectList()
        .map(docs -> {
            TicketStatsDTO.TicketStatsDTOBuilder builder = TicketStatsDTO.builder()
                .reportedByUser(0L)
                .errorProcessing(0L)
                .underReview(0L)
                .resolved(0L);

            for (Document doc : docs) {
                String status = doc.getString("_id");
                long count = ((Number) doc.get("count")).longValue();
                switch (status) {
                    case "REPORTED_BY_USER" -> builder.reportedByUser(count);
                    case "ERROR_PROCESSING" -> builder.errorProcessing(count);
                    case "UNDER_REVIEW"     -> builder.underReview(count);
                    case "RESOLVED"         -> builder.resolved(count);
                    default -> { /* ignore unknown statuses */ }
                }
            }
            return builder.build();
        });
    }

    @Override
    public Mono<TicketStatsDTO> countTicketsByStatusSinceAndUsernameIn(Instant since, List<String> usernames) {
        List<String> ticketStatuses = List.of(
            ConversationStatus.ERROR_PROCESSING.name(),
            ConversationStatus.REPORTED_BY_USER.name(),
            ConversationStatus.UNDER_REVIEW.name(),
            ConversationStatus.RESOLVED.name()
        );

        return mongoTemplate.aggregate(
            Aggregation.newAggregation(
                Aggregation.match(
                    Criteria.where("timestamp").gte(since)
                        .and("status").in(ticketStatuses)
                        .and("username").in(usernames)
                ),
                Aggregation.group("status").count().as("count")
            ),
            "conversations",
            Document.class
        )
        .collectList()
        .map(docs -> {
            TicketStatsDTO.TicketStatsDTOBuilder builder = TicketStatsDTO.builder()
                .reportedByUser(0L)
                .errorProcessing(0L)
                .underReview(0L)
                .resolved(0L);

            for (Document doc : docs) {
                String status = doc.getString("_id");
                long count = ((Number) doc.get("count")).longValue();
                switch (status) {
                    case "REPORTED_BY_USER" -> builder.reportedByUser(count);
                    case "ERROR_PROCESSING" -> builder.errorProcessing(count);
                    case "UNDER_REVIEW"     -> builder.underReview(count);
                    case "RESOLVED"         -> builder.resolved(count);
                    default -> { /* ignore unknown statuses */ }
                }
            }
            return builder.build();
        });
    }

    @Override
    public Mono<LastConversationInfo> findLastConversationByUsername(String username) {
        return mongoTemplate.aggregate(
            Aggregation.newAggregation(
                Aggregation.match(Criteria.where("username").is(username)),
                Aggregation.sort(Sort.by(Sort.Direction.ASC, "timestamp")),
                Aggregation.group("conversationId")
                    .first("userPrompt").as("title")
                    .max("timestamp").as("lastUpdated"),
                Aggregation.sort(Sort.by(Sort.Direction.DESC, "lastUpdated")),
                Aggregation.limit(1)
            ),
            "conversations",
            Document.class
        )
        .next()
        .map(doc -> new LastConversationInfo(
            doc.getString("_id"),
            doc.getString("title"),
            doc.getDate("lastUpdated").toInstant()
        ));
    }
}
