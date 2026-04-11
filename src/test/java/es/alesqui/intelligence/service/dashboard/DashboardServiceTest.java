package es.alesqui.intelligence.service.dashboard;

import es.alesqui.intelligence.dto.conversation.LastConversationInfo;
import es.alesqui.intelligence.model.api_spec.unified.UnifiedApiDocument;
import es.alesqui.intelligence.model.core.User;
import es.alesqui.intelligence.model.core.enums.Role;
import es.alesqui.intelligence.service.UnifiedApiService;
import es.alesqui.intelligence.service.access.ApiGroupLinkService;
import es.alesqui.intelligence.service.access.GroupManagementService;
import es.alesqui.intelligence.service.access.UserAdminService;
import es.alesqui.intelligence.service.conversation.ConversationService;
import es.alesqui.intelligence.service.identity.UserService;
import es.alesqui.intelligence.service.trial.TrialRegistrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for DashboardService.getSummary().
 * Verifies the role-conditional branching logic and edge cases.
 * Uses Mockito to mock all 7 dependencies — no Spring context needed.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DashboardService - getSummary")
class DashboardServiceTest {

    @Mock
    private UserService userService;
    @Mock
    private ConversationService conversationService;
    @Mock
    private ApiGroupLinkService apiGroupLinkService;
    @Mock
    private TrialRegistrationService trialRegistrationService;
    @Mock
    private UserAdminService userAdminService;
    @Mock
    private UnifiedApiService unifiedApiService;
    @Mock
    private GroupManagementService groupManagementService;

    @InjectMocks
    private DashboardService dashboardService;

    private static final String USER_ID   = "user-001";
    private static final String USERNAME  = "test@example.com";

    private UnifiedApiDocument apiDoc;

    @BeforeEach
    void setUp() {
        apiDoc = buildApiDoc("api-1", "My API", "A test API", true);
    }

    // ---------------------------------------------------------------------------
    // Helper builders
    // ---------------------------------------------------------------------------

    private User buildUser(String id, String username, Instant createdAt, Instant trialEndDate, Role... roles) {
        return User.builder()
                .id(id)
                .username(username)
                .createdAt(createdAt)
                .trialEndDate(trialEndDate)
                .roles(roles.length > 0 ? Set.of(roles) : Set.of())
                .build();
    }

    private UnifiedApiDocument buildApiDoc(String id, String name, String desc, boolean active) {
        return UnifiedApiDocument.builder()
                .id(id)
                .name(name)
                .description(desc)
                .active(active)
                .build();
    }

    /**
     * Stubs all calls that every getSummary() path exercises, regardless of role.
     */
    private void stubCommonMocks(User user) {
        String username = user.getUsername();
        String userId   = user.getId();
        Instant now     = Instant.now();

        when(userService.getCurrentUser()).thenReturn(Mono.just(user));
        when(conversationService.countDistinctConversations(username)).thenReturn(Mono.just(5L));
        when(conversationService.countDistinctConversationsSince(eq(username), any(Instant.class)))
                .thenReturn(Mono.just(2L));
        when(conversationService.countMessagesByUsername(username)).thenReturn(Mono.just(20L));
        when(conversationService.countChartsByUsername(username)).thenReturn(Mono.just(3L));
        when(conversationService.getLastConversation(username))
                .thenReturn(Mono.just(new LastConversationInfo("conv-1", "Title", now)));
        when(apiGroupLinkService.listVisibleApis(userId)).thenReturn(Flux.just(apiDoc));
    }

    // ---------------------------------------------------------------------------
    // Test cases
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("ROLE_TRIAL — includes trial info, activity, APIs, and support; admin is null")
    void getSummary_trialUser_includesTrialInfoAndActivity() {
        Instant trialEnd = Instant.now().plus(15, ChronoUnit.DAYS);
        User user = buildUser(USER_ID, USERNAME, Instant.now().minus(5, ChronoUnit.DAYS), trialEnd, Role.ROLE_TRIAL);

        stubCommonMocks(user);
        when(trialRegistrationService.getDaysRemaining(user)).thenReturn(15L);
        when(trialRegistrationService.isTrialExpired(user)).thenReturn(false);
        when(conversationService.countOpenTickets()).thenReturn(Mono.just(3L));

        StepVerifier.create(dashboardService.getSummary())
                .assertNext(response -> {
                    assertThat(response.getTrial()).isNotNull();
                    assertThat(response.getTrial().getDaysRemaining()).isEqualTo(15);
                    assertThat(response.getTrial().getTrialEndDate()).isEqualTo(trialEnd);
                    assertThat(response.getTrial().isExpired()).isFalse();

                    assertThat(response.getActivity()).isNotNull();
                    assertThat(response.getActivity().getTotalConversations()).isEqualTo(5L);
                    assertThat(response.getActivity().getConversationsLast7Days()).isEqualTo(2L);

                    assertThat(response.getApis()).hasSize(1);
                    assertThat(response.getApis().get(0).getId()).isEqualTo("api-1");

                    assertThat(response.getAdmin()).isNull();
                    assertThat(response.getSupport()).isNotNull();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("ROLE_BUSINESS — includes activity and APIs; trial, admin, and support are null")
    void getSummary_businessUser_includesActivityOnly() {
        User user = buildUser(USER_ID, USERNAME, Instant.now().minus(30, ChronoUnit.DAYS), null, Role.ROLE_BUSINESS);

        stubCommonMocks(user);

        StepVerifier.create(dashboardService.getSummary())
                .assertNext(response -> {
                    assertThat(response.getActivity()).isNotNull();
                    assertThat(response.getApis()).isNotNull();

                    assertThat(response.getTrial()).isNull();
                    assertThat(response.getAdmin()).isNull();
                    assertThat(response.getSupport()).isNull();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("ROLE_IT — includes support section with openTickets; admin and trial are null")
    void getSummary_itUser_includesSupportSection() {
        User user = buildUser(USER_ID, USERNAME, Instant.now().minus(60, ChronoUnit.DAYS), null, Role.ROLE_IT);

        stubCommonMocks(user);
        when(conversationService.countOpenTickets()).thenReturn(Mono.just(7L));

        StepVerifier.create(dashboardService.getSummary())
                .assertNext(response -> {
                    assertThat(response.getSupport()).isNotNull();
                    assertThat(response.getSupport().getOpenTickets()).isEqualTo(7L);

                    assertThat(response.getAdmin()).isNull();
                    assertThat(response.getTrial()).isNull();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("ROLE_TRIAL — includes support section with openTickets; admin is null")
    void getSummary_trialUser_includesSupportSection() {
        Instant trialEnd = Instant.now().plus(10, ChronoUnit.DAYS);
        User user = buildUser(USER_ID, USERNAME, Instant.now().minus(5, ChronoUnit.DAYS), trialEnd, Role.ROLE_TRIAL);

        stubCommonMocks(user);
        when(trialRegistrationService.getDaysRemaining(user)).thenReturn(10L);
        when(trialRegistrationService.isTrialExpired(user)).thenReturn(false);
        when(conversationService.countOpenTickets()).thenReturn(Mono.just(4L));

        StepVerifier.create(dashboardService.getSummary())
                .assertNext(response -> {
                    assertThat(response.getSupport()).isNotNull();
                    assertThat(response.getSupport().getOpenTickets()).isEqualTo(4L);

                    assertThat(response.getAdmin()).isNull();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("ROLE_SUPERADMIN — includes all sections: activity, APIs, admin, and support")
    void getSummary_superadminUser_includesAllSections() {
        User user = buildUser(USER_ID, USERNAME, Instant.now().minus(90, ChronoUnit.DAYS), null, Role.ROLE_SUPERADMIN);

        stubCommonMocks(user);
        when(userAdminService.countUsers()).thenReturn(Mono.just(100L));
        when(unifiedApiService.countApis()).thenReturn(Mono.just(10L));
        when(groupManagementService.countGroups()).thenReturn(Mono.just(5L));
        when(conversationService.countOpenTickets()).thenReturn(Mono.just(7L));

        StepVerifier.create(dashboardService.getSummary())
                .assertNext(response -> {
                    assertThat(response.getAdmin()).isNotNull();
                    assertThat(response.getAdmin().getTotalUsers()).isEqualTo(100L);
                    assertThat(response.getAdmin().getTotalApis()).isEqualTo(10L);
                    assertThat(response.getAdmin().getTotalGroups()).isEqualTo(5L);
                    assertThat(response.getAdmin().getOpenTickets()).isEqualTo(7L);

                    assertThat(response.getSupport()).isNotNull();
                    assertThat(response.getSupport().getOpenTickets()).isEqualTo(7L);

                    assertThat(response.getActivity()).isNotNull();
                    assertThat(response.getApis()).isNotNull();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("ROLE_BUSINESS with no conversations — all activity counts are 0 and lastConversation is null")
    void getSummary_userWithNoConversations_returnsZeroCounts() {
        User user = buildUser(USER_ID, USERNAME, Instant.now().minus(10, ChronoUnit.DAYS), null, Role.ROLE_BUSINESS);

        when(userService.getCurrentUser()).thenReturn(Mono.just(user));
        when(conversationService.countDistinctConversations(USERNAME)).thenReturn(Mono.just(0L));
        when(conversationService.countDistinctConversationsSince(eq(USERNAME), any(Instant.class)))
                .thenReturn(Mono.just(0L));
        when(conversationService.countMessagesByUsername(USERNAME)).thenReturn(Mono.just(0L));
        when(conversationService.countChartsByUsername(USERNAME)).thenReturn(Mono.just(0L));
        when(conversationService.getLastConversation(USERNAME)).thenReturn(Mono.empty());
        when(apiGroupLinkService.listVisibleApis(USER_ID)).thenReturn(Flux.just(apiDoc));

        StepVerifier.create(dashboardService.getSummary())
                .assertNext(response -> {
                    assertThat(response.getActivity()).isNotNull();
                    assertThat(response.getActivity().getTotalConversations()).isZero();
                    assertThat(response.getActivity().getConversationsLast7Days()).isZero();
                    assertThat(response.getActivity().getTotalMessages()).isZero();
                    assertThat(response.getActivity().getChartsGenerated()).isZero();
                    assertThat(response.getActivity().getLastConversation()).isNull();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("ROLE_TRIAL with expired trial — daysRemaining=0 and isExpired=true")
    void getSummary_userWithExpiredTrial_showsExpired() {
        Instant expiredDate = Instant.now().minus(2, ChronoUnit.DAYS);
        User user = buildUser(USER_ID, USERNAME, Instant.now().minus(16, ChronoUnit.DAYS), expiredDate, Role.ROLE_TRIAL);

        stubCommonMocks(user);
        when(trialRegistrationService.getDaysRemaining(user)).thenReturn(0L);
        when(trialRegistrationService.isTrialExpired(user)).thenReturn(true);
        when(conversationService.countOpenTickets()).thenReturn(Mono.just(0L));

        StepVerifier.create(dashboardService.getSummary())
                .assertNext(response -> {
                    assertThat(response.getTrial()).isNotNull();
                    assertThat(response.getTrial().getDaysRemaining()).isEqualTo(0);
                    assertThat(response.getTrial().isExpired()).isTrue();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("ROLE_BUSINESS — user info section contains correct username, roles, and memberSince")
    void getSummary_populatesUserInfoCorrectly() {
        Instant createdAt = Instant.now().minus(45, ChronoUnit.DAYS);
        User user = buildUser(USER_ID, USERNAME, createdAt, null, Role.ROLE_BUSINESS);

        stubCommonMocks(user);

        StepVerifier.create(dashboardService.getSummary())
                .assertNext(response -> {
                    assertThat(response.getUser()).isNotNull();
                    assertThat(response.getUser().getUsername()).isEqualTo(USERNAME);
                    assertThat(response.getUser().getRoles()).contains("ROLE_BUSINESS");
                    assertThat(response.getUser().getMemberSince()).isEqualTo(createdAt);
                })
                .verifyComplete();
    }
}
