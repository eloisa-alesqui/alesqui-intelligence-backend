package es.alesqui.postmangpt.interceptor;

import java.io.IOException;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class RequestInterceptor implements ClientHttpRequestInterceptor { 

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request, 
            byte[] body, 
            ClientHttpRequestExecution execution) throws IOException {
        
        log.info("=== OUTGOING REQUEST DEBUG ===");
        log.info("Method: {}", request.getMethod());
        log.info("URI: {}", request.getURI());
        log.info("Headers: {}", request.getHeaders());
        log.info("Body length: {}", body.length);
        
        String host = request.getURI().getHost();
        int port = request.getURI().getPort() == -1 ? 
            ("https".equals(request.getURI().getScheme()) ? 443 : 80) : 
            request.getURI().getPort();
            
        log.info("Target: {}:{}", host, port);
        log.info("Scheme: {}", request.getURI().getScheme());
        log.info("================================");
        
        try {
            ClientHttpResponse response = execution.execute(request, body);
            log.info("Response status: {}", response.getStatusCode());
            return response;
        } catch (Exception e) {
            log.error("Request failed: {}", e.getMessage());
            throw e;
        }
    }
}

