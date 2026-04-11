package es.alesqui.intelligence.service.dashboard;

import es.alesqui.intelligence.dto.conversation.LastConversationInfo;
import es.alesqui.intelligence.dto.dashboard.ActivityInfo;
import es.alesqui.intelligence.dto.dashboard.AdminInfo;
import es.alesqui.intelligence.dto.dashboard.ApiSummaryItem;
import es.alesqui.intelligence.dto.dashboard.DashboardSummaryResponse;
import es.alesqui.intelligence.dto.dashboard.SupportInfo;
import es.alesqui.intelligence.dto.dashboard.TrialInfo;
import es.alesqui.intelligence.dto.dashboard.UserInfo;
import es.alesqui.intelligence.model.core.User;
import es.alesqui.intelligence.model.core.enums.Role;
import es.alesqui.intelligence.service.UnifiedApiService;
import es.alesqui.intelligence.service.access.ApiGroupLinkService;
import es.alesqui.intelligence.service.access.GroupManagementService;
import es.alesqui.intelligence.service.access.UserAdminService;
import es.alesqui.intelligence.service.conversation.ConversationService;
import es.alesqui.intelligence.service.identity.UserService;
import es.alesqui.intelligence.service.trial.TrialRegistrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

/**
 * Assembles the Dashboard summary response for the currently authenticated user.
 *
 * This service acts as an orchestrator: it resolves the caller's identity and
 * then fans out to the relevant domain services in parallel to collect user
 * profile data, activity metrics, visible APIs, and role-specific sections.
 * Role-specific sections — trial, support, and admin — are included in the
 * response only when the authenticated user holds the corresponding role, keeping
 * the payload minimal for each user profile.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {

    private final UserService userService;
    private final ConversationService conversationService;
    private final ApiGroupLinkService apiGroupLinkService;
    private final TrialRegistrationService trialRegistrationService;
    private final UserAdminService userAdminService;
    private final UnifiedApiService unifiedApiService;
    private final GroupManagementService groupManagementService;

    /**
     * Returns a fully assembled DashboardSummaryResponse for the currently authenticated user.
     *
     * The response always includes the user profile section, aggregated activity metrics,
     * and the list of APIs visible to the user. On top of that, three optional sections
     * are conditionally appended:
     *   - ROLE_TRIAL: trial expiry details (days remaining, end date, expired flag).
     *   - ROLE_IT or ROLE_SUPERADMIN: support ticket summary with open ticket count.
     *   - ROLE_SUPERADMIN: platform-wide admin stats (users, APIs, groups, tickets).
     * When both the admin and support sections apply, they are fetched in parallel
     * via a single Mono.zip to avoid sequential latency.
     *
     * @return a Mono emitting the assembled DashboardSummaryResponse for the caller.
     */
    public Mono<DashboardSummaryResponse> getSummary() {
        return userService.getCurrentUser()
                .flatMap(user -> {
                    Set<Role> roles = user.getRoles();

                    Mono<ActivityInfo> activityInfo = buildActivityInfo(user.getUsername());
                    Mono<List<ApiSummaryItem>> apiList = buildApiList(user.getId());

                    return Mono.zip(activityInfo, apiList)
                            .flatMap(tuple -> {
                                DashboardSummaryResponse.DashboardSummaryResponseBuilder builder =
                                        DashboardSummaryResponse.builder()
                                                .user(buildUserInfo(user))
                                                .activity(tuple.getT1())
                                                .apis(tuple.getT2());

                                if (roles != null && roles.contains(Role.ROLE_TRIAL)) {
                                    builder.trial(buildTrialInfo(user));
                                }

                                boolean isAdmin = roles != null && roles.contains(Role.ROLE_SUPERADMIN);
                                boolean isSupport = isAdmin || (roles != null &&
                                        (roles.contains(Role.ROLE_IT) || roles.contains(Role.ROLE_TRIAL)));

                                if (isAdmin && isSupport) {
                                    return Mono.zip(buildAdminInfo(), buildSupportInfo())
                                            .map(t2 -> builder.admin(t2.getT1()).support(t2.getT2()).build());
                                } else if (isSupport) {
                                    return buildSupportInfo()
                                            .map(s -> builder.support(s).build());
                                }
                                return Mono.just(builder.build());
                            });
                });
    }

    /**
     * Maps the authenticated User entity to the UserInfo section of the Dashboard.
     * Role names are derived from the user's Role set and serialised as strings.
     * Returns a UserInfo with the username, role name list, and account creation timestamp.
     *
     * @param user the authenticated user resolved from the security context.
     * @return a UserInfo populated with the user's profile data.
     */
    private UserInfo buildUserInfo(User user) {
        List<String> roleNames = user.getRoles() == null
                ? List.of()
                : user.getRoles().stream().map(Role::name).toList();

        return UserInfo.builder()
                .username(user.getUsername())
                .roles(roleNames)
                .memberSince(user.getCreatedAt())
                .build();
    }

    /**
     * Builds the TrialInfo section for users on a time-limited trial plan.
     * Delegates to TrialRegistrationService to compute the days remaining and
     * determine whether the trial has already expired. The trial end date is read
     * directly from the User entity.
     *
     * @param user the authenticated trial user.
     * @return a TrialInfo with the days remaining, end date, and expiry flag.
     */
    private TrialInfo buildTrialInfo(User user) {
        return TrialInfo.builder()
                .daysRemaining((int) trialRegistrationService.getDaysRemaining(user))
                .trialEndDate(user.getTrialEndDate())
                .isExpired(trialRegistrationService.isTrialExpired(user))
                .build();
    }

    /**
     * Fetches all five activity metrics for the given user in parallel and assembles
     * them into an ActivityInfo.
     *
     * The five queries run concurrently via Mono.zip: total distinct conversations,
     * conversations started in the last 7 days, total messages sent, charts generated,
     * and the most recently active conversation. Because getLastConversation may complete
     * empty for users with no history, a sentinel LastConversationInfo with all-null fields
     * is substituted as the default value so that the zip tuple is always complete. The
     * sentinel is mapped back to null in the resulting ActivityInfo.
     *
     * @param username the authenticated user's username.
     * @return a Mono emitting the assembled ActivityInfo.
     */
    private Mono<ActivityInfo> buildActivityInfo(String username) {
        Instant sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS);

        Mono<Long> total = conversationService.countDistinctConversations(username);
        Mono<Long> recent = conversationService.countDistinctConversationsSince(username, sevenDaysAgo);
        Mono<Long> msgs = conversationService.countMessagesByUsername(username);
        Mono<Long> charts = conversationService.countChartsByUsername(username);
        Mono<LastConversationInfo> last = conversationService.getLastConversation(username)
                .defaultIfEmpty(new LastConversationInfo());

        return Mono.zip(total, recent, msgs, charts, last)
                .map(t -> ActivityInfo.builder()
                        .totalConversations(t.getT1())
                        .conversationsLast7Days(t.getT2())
                        .totalMessages(t.getT3())
                        .chartsGenerated(t.getT4())
                        .lastConversation(t.getT5().getConversationId() != null ? t.getT5() : null)
                        .build());
    }

    /**
     * Resolves the list of APIs visible to the user and maps each document to a
     * lightweight ApiSummaryItem. Visibility is determined by ApiGroupLinkService,
     * which applies group-membership rules and grants SUPERADMIN users access to all
     * APIs regardless of group configuration.
     *
     * @param userId the ID of the authenticated user.
     * @return a Mono emitting the list of ApiSummaryItems visible to the user.
     */
    private Mono<List<ApiSummaryItem>> buildApiList(String userId) {
        return apiGroupLinkService.listVisibleApis(userId)
                .map(doc -> ApiSummaryItem.builder()
                        .id(doc.getId())
                        .name(doc.getName())
                        .description(doc.getDescription())
                        .active(doc.isActive())
                        .build())
                .collectList();
    }

    /**
     * Fetches platform-wide statistics for the admin section of the Dashboard.
     * Runs four count queries in parallel via Mono.zip: registered users, unified
     * API documents, access groups, and open support tickets. This section is only
     * built when the caller holds ROLE_SUPERADMIN.
     *
     * @return a Mono emitting an AdminInfo with the four platform-wide counters.
     */
    private Mono<AdminInfo> buildAdminInfo() {
        return Mono.zip(
                userAdminService.countUsers(),
                unifiedApiService.countApis(),
                groupManagementService.countGroups(),
                conversationService.countOpenTickets()
        ).map(t -> AdminInfo.builder()
                .totalUsers(t.getT1())
                .totalApis(t.getT2())
                .totalGroups(t.getT3())
                .openTickets(t.getT4())
                .build());
    }

    /**
     * Fetches the current count of open support tickets for the support section of the Dashboard.
     * A ticket is considered open when its status is either REPORTED_BY_USER or ERROR_PROCESSING.
     * This section is built for users with ROLE_IT or ROLE_SUPERADMIN.
     *
     * @return a Mono emitting a SupportInfo with the open ticket count.
     */
    private Mono<SupportInfo> buildSupportInfo() {
        return conversationService.countOpenTickets()
                .map(count -> SupportInfo.builder().openTickets(count).build());
    }
}
