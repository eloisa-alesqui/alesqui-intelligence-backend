package es.alesqui.intelligence.config;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

import es.alesqui.intelligence.interceptor.RequestInterceptor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.ProxyProvider;

/**
 * Configuration class for HTTP clients used throughout the application.
 * 
 * This class provides centralized configuration for:
 * - RestClient with synchronous HTTP operations
 * - WebClient with reactive HTTP operations
 * - Proxy configuration for both clients
 * - Custom interceptors and logging
 * - Timeout configurations
 * 
 * These HTTP clients can be reused across different parts of the application,
 * not just for AI chat functionality.
 */
@Configuration
@Slf4j
public class HttpClientConfig {

    /**
     * Custom request interceptor for handling HTTP requests.
     */
    @Autowired
    private RequestInterceptor requestInterceptor;

    /**
     * Proxy server hostname injected from application properties.
     * Expected property: proxy.host
     */
    @Value("${proxy.host}")
    private String proxyHost;
    
    /**
     * Proxy server port injected from application properties.
     * Expected property: proxy.port
     */
    @Value("${proxy.port}")
    private int proxyPort;

    /**
     * Creates and configures a RestClient bean with proxy support and custom interceptors.
     * 
     * The RestClient is configured with:
     * - HTTP proxy using the configured host and port
     * - Connection timeout of 30 seconds
     * - Read timeout of 120 seconds
     * - Custom request interceptor for request processing
     * 
     * @return a configured RestClient instance with proxy and timeout settings
     */
    @Bean
    public RestClient restClient() {
        Proxy proxy = new Proxy(Proxy.Type.HTTP, 
            new InetSocketAddress(proxyHost, proxyPort));
        
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setProxy(proxy);
        factory.setConnectTimeout(Duration.ofSeconds(30));
        factory.setReadTimeout(Duration.ofSeconds(120));
        
        log.info("✅ RestClient configured with proxy: {}:{}", proxyHost, proxyPort);
        
        return RestClient.builder()
                .requestFactory(factory)
                .requestInterceptor(requestInterceptor)
                .build();
    }
    
    /**
     * Creates and configures a WebClient bean with reactive HTTP proxy support.
     * 
     * The WebClient is configured with:
     * - Reactive HTTP proxy using Reactor Netty
     * - Response timeout of 120 seconds
     * - Request and response logging filters
     * - Custom connector with proxy configuration
     * 
     * @return a configured WebClient instance with reactive proxy support and logging
     */
    @Bean
    public WebClient webClient() {
        HttpClient httpClient = HttpClient.create()
            .proxy(proxySpec -> proxySpec
                .type(ProxyProvider.Proxy.HTTP)
                .host(proxyHost)
                .port(proxyPort))
            .responseTimeout(Duration.ofSeconds(120));
        
        ReactorClientHttpConnector connector = 
            new ReactorClientHttpConnector(httpClient);
        
        return WebClient.builder()
            .clientConnector(connector)
            .filter(ExchangeFilterFunction.ofRequestProcessor(request -> {
                log.info("🚀 WebClient request: {} {}", 
                    request.method(), request.url());
                return Mono.just(request);
            }))
            .filter(ExchangeFilterFunction.ofResponseProcessor(response -> {
                log.info("✅ WebClient response: {}", response.statusCode());
                return Mono.just(response);
            }))
            .build();
    }
}
