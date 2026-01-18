package es.alesqui.intelligence.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import es.alesqui.intelligence.config.DeploymentConfig;
import es.alesqui.intelligence.dto.DeploymentInfoResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Public REST controller for deployment information.
 * 
 * Purpose:
 * - Provides public endpoint for frontend customization
 * - No authentication required (permitAll in SecurityConfig)
 * - Returns deployment mode, company name, and feature flags
 * 
 * Endpoints:
 * - GET /api/public/deployment-info - Get deployment configuration
 */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
@Slf4j
public class DeploymentInfoController {
    
    private final DeploymentConfig deploymentConfig;
    
    /**
     * Returns deployment information for frontend customization.
     * No authentication required.
     * 
     * Response:
     * {
     *   "mode": "TRIAL" | "CORPORATE",
     *   "companyName": "Alesqui Intelligence",
     *   "selfRegistrationEnabled": true | false
     * }
     * 
     * @return Mono containing deployment information
     */
    @GetMapping("/deployment-info")
    public Mono<DeploymentInfoResponse> getDeploymentInfo() {
        log.debug("[DeploymentInfo] Requested deployment information");
        
        return Mono.just(DeploymentInfoResponse.builder()
                .mode(deploymentConfig.getMode().name())
                .companyName(deploymentConfig.getCompanyName())
                .selfRegistrationEnabled(deploymentConfig.isSelfRegistrationEnabled())
                .build());
    }
}
