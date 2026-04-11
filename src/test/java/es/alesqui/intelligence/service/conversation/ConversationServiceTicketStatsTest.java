package es.alesqui.intelligence.service.conversation;

import es.alesqui.intelligence.dto.conversation.TicketStatsDTO;
import es.alesqui.intelligence.model.core.User;
import es.alesqui.intelligence.model.core.enums.Role;
import es.alesqui.intelligence.repository.ConversationRecordRepository;
import es.alesqui.intelligence.service.access.GroupMembershipService;
import es.alesqui.intelligence.service.identity.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTicketStatsTest {

    @Mock
    private ConversationRecordRepository repository;

    @Mock
    private GroupMembershipService groupMembershipService;

    @Mock
    private UserService userService;

    @InjectMocks
    private ConversationService conversationService;

    private static final User SUPERADMIN = User.builder()
            .id("sa-1")
            .roles(Set.of(Role.ROLE_SUPERADMIN))
            .build();

    private static final User IT_USER = User.builder()
            .id("it-1")
            .roles(Set.of(Role.ROLE_IT))
            .build();

    // ── SUPERADMIN path ────────────────────────────────────────────────────

    @Test
    void getTicketStats_superAdmin_queriesAllTicketsWithThirtyDayWindow() {
        TicketStatsDTO expected = new TicketStatsDTO(3, 2, 1, 5);
        when(userService.getCurrentUser()).thenReturn(Mono.just(SUPERADMIN));
        when(repository.countTicketsByStatusSince(any())).thenReturn(Mono.just(expected));

        StepVerifier.create(conversationService.getTicketStats())
                .assertNext(dto -> assertThat(dto).isEqualTo(expected))
                .verifyComplete();

        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        verify(repository).countTicketsByStatusSince(captor.capture());

        assertThat(captor.getValue())
                .isCloseTo(Instant.now().minus(30, ChronoUnit.DAYS), within(1, ChronoUnit.SECONDS));
    }

    // ── Non-SUPERADMIN (group-filtered) path ──────────────────────────────

    @Test
    void getTicketStats_nonSuperAdmin_queriesOnlyGroupMembers() {
        TicketStatsDTO expected = new TicketStatsDTO(1, 0, 1, 0);
        when(userService.getCurrentUser()).thenReturn(Mono.just(IT_USER));
        when(groupMembershipService.getGroupIdsByUserId("it-1")).thenReturn(Flux.just("g-1"));
        when(groupMembershipService.getUserIdsByGroupIds(List.of("g-1"))).thenReturn(Flux.just("u-1", "u-2"));
        when(userService.getUsernamesByIds(anyList())).thenReturn(Flux.just("alice@example.com", "bob@example.com"));
        when(repository.countTicketsByStatusSinceAndUsernameIn(any(), anyList())).thenReturn(Mono.just(expected));

        StepVerifier.create(conversationService.getTicketStats())
                .assertNext(dto -> assertThat(dto).isEqualTo(expected))
                .verifyComplete();

        ArgumentCaptor<Instant> sinceCaptor = ArgumentCaptor.forClass(Instant.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> usernamesCaptor = ArgumentCaptor.forClass(List.class);
        verify(repository).countTicketsByStatusSinceAndUsernameIn(sinceCaptor.capture(), usernamesCaptor.capture());

        assertThat(sinceCaptor.getValue())
                .isCloseTo(Instant.now().minus(30, ChronoUnit.DAYS), within(1, ChronoUnit.SECONDS));
        assertThat(usernamesCaptor.getValue()).containsExactlyInAnyOrder("alice@example.com", "bob@example.com");
    }

    @Test
    void getTicketStats_nonSuperAdmin_noGroupMemberships_returnsAllZeros() {
        when(userService.getCurrentUser()).thenReturn(Mono.just(IT_USER));
        when(groupMembershipService.getGroupIdsByUserId("it-1")).thenReturn(Flux.empty());

        StepVerifier.create(conversationService.getTicketStats())
                .assertNext(dto -> {
                    assertThat(dto.getReportedByUser()).isZero();
                    assertThat(dto.getErrorProcessing()).isZero();
                    assertThat(dto.getUnderReview()).isZero();
                    assertThat(dto.getResolved()).isZero();
                })
                .verifyComplete();
    }
}
