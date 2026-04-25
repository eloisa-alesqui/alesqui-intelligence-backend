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

    @Value("${proxy.host:#{null}}")
    private String proxyHost;
    
    @Value("${proxy.port:#{null}}")
    private Integer proxyPort;

    /**
     * Creates and configures a RestClient bean with optional proxy support.
     *
     * @return a configured RestClient instance.
     */
    @Bean
    public RestClient restClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(30));
        factory.setReadTimeout(Duration.ofSeconds(120));
        
        if (proxyHost != null && !proxyHost.isBlank() && proxyPort != null && proxyPort > 0) {
            Proxy proxy = new Proxy(Proxy.Type.HTTP, 
                new InetSocketAddress(proxyHost, proxyPort));
            factory.setProxy(proxy);
            log.info("✅ RestClient configured with proxy: {}:{}", proxyHost, proxyPort);
        } else {
            log.info("✅ RestClient configured without proxy");
        }
        
        return RestClient.builder()
                .requestFactory(factory)
                .requestInterceptor(requestInterceptor)
                .build();
    }
    
    /**
     * Creates a primary RestClient.Builder bean with a long read timeout, used by
     * Spring AI auto-configurations (e.g. Ollama). No proxy is configured here —
     * proxy-aware HTTP for OpenAI is provided by the {@link #restClient()} bean above.
     * The 5-minute read timeout accommodates slow self-hosted models (e.g. Gemma) that
     * may take tens of seconds to load and generate.
     */
    @Bean
    @Primary
    public RestClient.Builder restClientBuilder() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(30));
        factory.setReadTimeout(Duration.ofMinutes(5));
        return RestClient.builder().requestFactory(factory);
    }

    /**
     * Creates a pre-configured, primary WebClient.Builder bean with optional proxy support.
     * By marking this bean with @Primary, we instruct Spring to use this builder as the
     * default choice across the application, resolving autoconfiguration conflicts.
     *
     * @return a configured WebClient.Builder instance with optional proxy support and logging.
     */
    @Bean
    @Primary
    public WebClient.Builder webClientBuilder() {
        // 1. Configure the underlying HttpClient with optional proxy settings
        HttpClient httpClient = HttpClient.create()
            .responseTimeout(Duration.ofSeconds(120)); // Default response timeout
        
        if (proxyHost != null && !proxyHost.isBlank() && proxyPort != null && proxyPort > 0) {
            httpClient = httpClient.proxy(proxySpec -> proxySpec
                .type(ProxyProvider.Proxy.HTTP)
                .host(proxyHost)
                .port(proxyPort)
                .nonProxyHosts("localhost|127.0.0.1"));
            log.info("✅ WebClient.Builder is now configured to use proxy: {}:{}", proxyHost, proxyPort);
        } else {
            log.info("✅ WebClient.Builder configured without proxy");
        }
        
        // 2. Create a connector with the configured HttpClient
        ReactorClientHttpConnector connector = new ReactorClientHttpConnector(httpClient);

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
