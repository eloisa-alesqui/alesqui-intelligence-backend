package es.alesqui.intelligence.repository;

import es.alesqui.intelligence.dto.conversation.ConversationSummaryDTO;
import es.alesqui.intelligence.dto.conversation.LastConversationInfo;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

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
