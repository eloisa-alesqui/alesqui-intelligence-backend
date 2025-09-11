package es.alesqui.intelligence.config;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
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
 * This class now provides a pre-configured WebClient.Builder bean to ensure
 * proxy settings are propagated to all services that use it.
 */
@Configuration
@Slf4j
public class HttpClientConfig {

    @Autowired
    private RequestInterceptor requestInterceptor;

    @Value("${proxy.host}")
    private String proxyHost;
    
    @Value("${proxy.port}")
    private int proxyPort;

    /**
     * Creates and configures a RestClient bean with proxy support.
     * This remains unchanged.
     *
     * @return a configured RestClient instance.
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
     * Creates a pre-configured, primary WebClient.Builder bean with proxy support.
     * By marking this bean with @Primary, we instruct Spring to use this builder as the
     * default choice across the application, resolving autoconfiguration conflicts.
     *
     * @return a configured WebClient.Builder instance with proxy support and logging.
     */
    @Bean
    @Primary
    public WebClient.Builder webClientBuilder() {
        // 1. Configure the underlying HttpClient with proxy settings
        HttpClient httpClient = HttpClient.create()
            .proxy(proxySpec -> proxySpec
                .type(ProxyProvider.Proxy.HTTP)
                .host(proxyHost)
                .port(proxyPort)
                .nonProxyHosts("localhost|127.0.0.1"))
            .responseTimeout(Duration.ofSeconds(120)); // Default response timeout
        
        // 2. Create a connector with the configured HttpClient
        ReactorClientHttpConnector connector = new ReactorClientHttpConnector(httpClient);
        
        log.info("✅ WebClient.Builder is now configured to use proxy: {}:{}", proxyHost, proxyPort);

        // 3. Return a WebClient.Builder pre-configured with the connector and filters
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
            }));
    }
}
