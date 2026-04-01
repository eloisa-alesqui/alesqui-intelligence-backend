package es.alesqui.intelligence.controller;

import es.alesqui.intelligence.config.DeploymentConfig;
import es.alesqui.intelligence.config.TestSecurityConfig;
import es.alesqui.intelligence.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.mockito.BDDMockito.given;

/**
 * Controller-layer tests for DeploymentInfoController.
 * Uses TestSecurityConfig to bypass the JWT filter (public endpoint).
 */
@WebFluxTest(DeploymentInfoController.class)
@Import(TestSecurityConfig.class)
class DeploymentInfoControllerTest {

    @Autowired
    WebTestClient webClient;

    @MockitoBean DeploymentConfig deploymentConfig;
    @MockitoBean JwtService jwtService;
    @MockitoBean ReactiveUserDetailsService reactiveUserDetailsService;

    @Test
    void getDeploymentInfo_trialMode_returns200() {
        given(deploymentConfig.getMode()).willReturn(DeploymentConfig.DeploymentMode.TRIAL);
        given(deploymentConfig.getCompanyName()).willReturn("TestCo");
        given(deploymentConfig.isSelfRegistrationEnabled()).willReturn(true);

        webClient.get()
                .uri("/api/public/deployment-info")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.mode").isEqualTo("TRIAL")
                .jsonPath("$.selfRegistrationEnabled").isEqualTo(true);
    }

    @Test
    void getDeploymentInfo_corporateMode_returns200() {
        given(deploymentConfig.getMode()).willReturn(DeploymentConfig.DeploymentMode.CORPORATE);
        given(deploymentConfig.getCompanyName()).willReturn("CorpCo");
        given(deploymentConfig.isSelfRegistrationEnabled()).willReturn(false);

        webClient.get()
                .uri("/api/public/deployment-info")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.mode").isEqualTo("CORPORATE")
                .jsonPath("$.selfRegistrationEnabled").isEqualTo(false);
    }
}
