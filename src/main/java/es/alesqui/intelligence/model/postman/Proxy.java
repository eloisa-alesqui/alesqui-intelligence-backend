package es.alesqui.intelligence.model.postman;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.springframework.data.mongodb.core.mapping.Field;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Using the Proxy, you can configure your custom proxy into the postman for
 * particular url match Follows the Postman Collection Format v2.1.0
 * specification
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Proxy {

	/**
	 * The proxy server host
	 */
	@Field("host")
	@JsonProperty("host")
	private String host;

	/**
	 * The proxy server port
	 */
	@Field("port")
	@JsonProperty("port")
	private Integer port;

	/**
	 * The tunneling details for the proxy config
	 */
	@Field("tunnel")
	@JsonProperty("tunnel")
	private Boolean tunnel;

	/**
	 * When set to true, ignores this proxy configuration entity
	 */
	@Field("disabled")
	@JsonProperty("disabled")
	@Builder.Default
	private Boolean disabled = false;

	/**
	 * Creates a new Proxy with host and port
	 * 
	 * @param host Proxy host
	 * @param port Proxy port
	 * @return Proxy instance
	 */
	public static Proxy create(String host, int port) {
		return Proxy.builder().host(host).port(port).build();
	}

	/**
	 * Creates a new Proxy with host, port and tunnel setting
	 * 
	 * @param host   Proxy host
	 * @param port   Proxy port
	 * @param tunnel Whether to use tunneling
	 * @return Proxy instance
	 */
	public static Proxy create(String host, int port, boolean tunnel) {
		return Proxy.builder().host(host).port(port).tunnel(tunnel).build();
	}

	/**
	 * Sets the host for this proxy
	 * 
	 * @param host Proxy host
	 * @return this instance for method chaining
	 */
	public Proxy withHost(String host) {
		this.host = host;
		return this;
	}

	/**
	 * Sets the port for this proxy
	 * 
	 * @param port Proxy port
	 * @return this instance for method chaining
	 */
	public Proxy withPort(int port) {
		this.port = port;
		return this;
	}

	/**
	 * Sets the tunnel setting for this proxy
	 * 
	 * @param tunnel Whether to use tunneling
	 * @return this instance for method chaining
	 */
	public Proxy withTunnel(boolean tunnel) {
		this.tunnel = tunnel;
		return this;
	}

	/**
	 * Enables this proxy (sets disabled to false)
	 * 
	 * @return this instance for method chaining
	 */
	public Proxy enable() {
		this.disabled = false;
		return this;
	}

	/**
	 * Disables this proxy (sets disabled to true)
	 * 
	 * @return this instance for method chaining
	 */
	public Proxy disable() {
		this.disabled = true;
		return this;
	}

	/**
	 * Sets the disabled state of this proxy
	 * 
	 * @param disabled Whether the proxy should be disabled
	 * @return this instance for method chaining
	 */
	public Proxy setDisabled(boolean disabled) {
		this.disabled = disabled;
		return this;
	}

	/**
	 * Checks if this proxy is enabled (not disabled)
	 * 
	 * @return true if enabled, false if disabled
	 */
	public boolean isEnabled() {
		return disabled == null || !disabled;
	}

	/**
	 * Checks if this proxy is disabled
	 * 
	 * @return true if disabled, false if enabled
	 */
	public boolean isDisabled() {
		return disabled != null && disabled;
	}

	/**
	 * Checks if tunneling is enabled
	 * 
	 * @return true if tunneling is enabled
	 */
	public boolean isTunnelEnabled() {
		return tunnel != null && tunnel;
	}

	/**
	 * Gets the proxy URL in format host:port
	 * 
	 * @return Proxy URL string
	 */
	public String getProxyUrl() {
		if (host == null) {
			return null;
		}
		return port != null ? host + ":" + port : host;
	}

	/**
	 * Validates if the proxy configuration is valid
	 * 
	 * @return true if host is not null and not empty
	 */
	public boolean isValid() {
		return host != null && !host.trim().isEmpty();
	}

	/**
	 * Creates a copy of this Proxy
	 * 
	 * @return A new Proxy instance with the same values
	 */
	public Proxy copy() {
		return Proxy.builder().host(this.host).port(this.port).tunnel(this.tunnel).disabled(this.disabled).build();
	}

	@Override
	public String toString() {
		return String.format("Proxy{host='%s', port=%d, tunnel=%s, disabled=%s}", host, port, tunnel, disabled);
	}
}
