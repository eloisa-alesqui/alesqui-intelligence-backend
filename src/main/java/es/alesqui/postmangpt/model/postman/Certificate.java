package es.alesqui.postmangpt.model.postman;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

import org.springframework.data.mongodb.core.mapping.Field;

import java.util.ArrayList;

/**
 * A representation of an SSL certificate Follows the Postman Collection Format
 * v2.1.0 specification
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Certificate {

	/**
	 * A name for the certificate for user reference
	 */
	@Field("name")
	@JsonProperty("name")
	private String name;

	/**
	 * A list of URL match pattern strings, to identify URLs this certificate can be
	 * used for
	 */
	@Field("matches")
	@JsonProperty("matches")
	private List<String> matches;

	/**
	 * An object containing path to file containing private key, on the file system
	 */
	@Field("key")
	@JsonProperty("key")
	private CertificateFile key;

	/**
	 * An object containing path to file certificate, on the file system
	 */
	@Field("cert")
	@JsonProperty("cert")
	private CertificateFile cert;

	/**
	 * The passphrase for the certificate
	 */
	@Field("passphrase")
	@JsonProperty("passphrase")
	private String passphrase;

	/**
	 * Represents a certificate file (key or cert)
	 */
	@Data
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public static class CertificateFile {

		/**
		 * The path to file containing key/certificate, on the file system
		 */
		@JsonProperty("src")
		private String src;

		/**
		 * Creates a copy of this CertificateFile
		 * 
		 * @return A new CertificateFile instance with the same values
		 */
		public CertificateFile copy() {
			return CertificateFile.builder()
					.src(this.src)
					.build();
		}

		/**
		 * Validates if the certificate file is valid
		 * 
		 * @return true if src is not null and not empty
		 */
		public boolean isValid() {
			return src != null && !src.trim().isEmpty();
		}

		/**
		 * Gets the file name from the path
		 * 
		 * @return File name or null if path is invalid
		 */
		public String getFileName() {
			if (src == null || src.trim().isEmpty()) {
				return null;
			}
			
			String path = src.replace('\\', '/');
			int lastSlash = path.lastIndexOf('/');
			return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
		}

		/**
		 * Gets the file extension
		 * 
		 * @return File extension (without dot) or null
		 */
		public String getFileExtension() {
			String fileName = getFileName();
			if (fileName == null) {
				return null;
			}
			
			int lastDot = fileName.lastIndexOf('.');
			return lastDot >= 0 ? fileName.substring(lastDot + 1) : null;
		}

		/**
		 * Checks if this is a key file (based on common extensions)
		 * 
		 * @return true if appears to be a key file
		 */
		public boolean isKeyFile() {
			String extension = getFileExtension();
			if (extension == null) {
				return false;
			}
			
			String ext = extension.toLowerCase();
			return ext.equals("key") || ext.equals("pem") || ext.equals("p12") || 
				   ext.equals("pfx") || ext.equals("pkcs12");
		}

		/**
		 * Checks if this is a certificate file (based on common extensions)
		 * 
		 * @return true if appears to be a certificate file
		 */
		public boolean isCertFile() {
			String extension = getFileExtension();
			if (extension == null) {
				return false;
			}
			
			String ext = extension.toLowerCase();
			return ext.equals("crt") || ext.equals("cer") || ext.equals("pem") || 
				   ext.equals("p12") || ext.equals("pfx") || ext.equals("der");
		}

		@Override
		public String toString() {
			return String.format("CertificateFile{src='%s'}", src);
		}
	}

	/**
	 * Creates a new Certificate with the specified name
	 * 
	 * @param name Certificate name
	 * @return Certificate instance
	 */
	public static Certificate create(String name) {
		return Certificate.builder().name(name).build();
	}

	/**
	 * Creates a new Certificate with name and matches
	 * 
	 * @param name    Certificate name
	 * @param matches List of URL patterns
	 * @return Certificate instance
	 */
	public static Certificate create(String name, List<String> matches) {
		return Certificate.builder().name(name).matches(matches).build();
	}

	/**
	 * Creates a certificate file reference
	 * 
	 * @param src Path to the certificate file
	 * @return CertificateFile instance
	 */
	public static CertificateFile createFile(String src) {
		return CertificateFile.builder().src(src).build();
	}

	/**
	 * Creates a complete certificate with key and cert files
	 * 
	 * @param name     Certificate name
	 * @param keyPath  Path to private key file
	 * @param certPath Path to certificate file
	 * @return Certificate instance
	 */
	public static Certificate create(String name, String keyPath, String certPath) {
		return Certificate.builder()
				.name(name)
				.key(createFile(keyPath))
				.cert(createFile(certPath))
				.build();
	}

	/**
	 * Creates a complete certificate with key, cert files and passphrase
	 * 
	 * @param name       Certificate name
	 * @param keyPath    Path to private key file
	 * @param certPath   Path to certificate file
	 * @param passphrase Certificate passphrase
	 * @return Certificate instance
	 */
	public static Certificate create(String name, String keyPath, String certPath, String passphrase) {
		return Certificate.builder()
				.name(name)
				.key(createFile(keyPath))
				.cert(createFile(certPath))
				.passphrase(passphrase)
				.build();
	}

	/**
	 * Sets the key file for this certificate
	 * 
	 * @param keyPath Path to the private key file
	 * @return this instance for method chaining
	 */
	public Certificate withKey(String keyPath) {
		this.key = createFile(keyPath);
		return this;
	}

	/**
	 * Sets the key file for this certificate
	 * 
	 * @param keyFile CertificateFile for the key
	 * @return this instance for method chaining
	 */
	public Certificate withKey(CertificateFile keyFile) {
		this.key = keyFile;
		return this;
	}

	/**
	 * Sets the certificate file for this certificate
	 * 
	 * @param certPath Path to the certificate file
	 * @return this instance for method chaining
	 */
	public Certificate withCert(String certPath) {
		this.cert = createFile(certPath);
		return this;
	}

	/**
	 * Sets the certificate file for this certificate
	 * 
	 * @param certFile CertificateFile for the certificate
	 * @return this instance for method chaining
	 */
	public Certificate withCert(CertificateFile certFile) {
		this.cert = certFile;
		return this;
	}

	/**
	 * Sets the passphrase for this certificate
	 * 
	 * @param passphrase Certificate passphrase
	 * @return this instance for method chaining
	 */
	public Certificate withPassphrase(String passphrase) {
		this.passphrase = passphrase;
		return this;
	}

	/**
	 * Sets the name for this certificate
	 * 
	 * @param name Certificate name
	 * @return this instance for method chaining
	 */
	public Certificate withName(String name) {
		this.name = name;
		return this;
	}

	/**
	 * Sets the matches for this certificate
	 * 
	 * @param matches List of URL patterns
	 * @return this instance for method chaining
	 */
	public Certificate withMatches(List<String> matches) {
		this.matches = matches != null ? new ArrayList<>(matches) : null;
		return this;
	}

	/**
	 * Adds a URL match pattern
	 * 
	 * @param pattern URL pattern string
	 * @return this instance for method chaining
	 */
	public Certificate addMatch(String pattern) {
		if (this.matches == null) {
			this.matches = new ArrayList<>();
		}
		this.matches.add(pattern);
		return this;
	}

	/**
	 * Adds multiple URL match patterns
	 * 
	 * @param patterns URL pattern strings
	 * @return this instance for method chaining
	 */
	public Certificate addMatches(String... patterns) {
		if (this.matches == null) {
			this.matches = new ArrayList<>();
		}
		for (String pattern : patterns) {
			this.matches.add(pattern);
		}
		return this;
	}

	/**
	 * Adds multiple URL match patterns from list
	 * 
	 * @param patterns List of URL pattern strings
	 * @return this instance for method chaining
	 */
	public Certificate addMatches(List<String> patterns) {
		if (patterns != null) {
			if (this.matches == null) {
				this.matches = new ArrayList<>();
			}
			this.matches.addAll(patterns);
		}
		return this;
	}

	/**
	 * Removes a URL match pattern
	 * 
	 * @param pattern Pattern to remove
	 * @return this instance for method chaining
	 */
	public Certificate removeMatch(String pattern) {
		if (this.matches != null) {
			this.matches.remove(pattern);
		}
		return this;
	}

	/**
	 * Clears all URL match patterns
	 * 
	 * @return this instance for method chaining
	 */
	public Certificate clearMatches() {
		if (this.matches != null) {
			this.matches.clear();
		}
		return this;
	}

	/**
	 * Checks if this certificate has a key file
	 * 
	 * @return true if has key file
	 */
	public boolean hasKey() {
		return key != null && key.isValid();
	}

	/**
	 * Checks if this certificate has a cert file
	 * 
	 * @return true if has cert file
	 */
	public boolean hasCert() {
		return cert != null && cert.isValid();
	}

	/**
	 * Checks if this certificate has a passphrase
	 * 
	 * @return true if has passphrase
	 */
	public boolean hasPassphrase() {
		return passphrase != null && !passphrase.trim().isEmpty();
	}

	/**
	 * Checks if this certificate has matches
	 * 
	 * @return true if has matches
	 */
	public boolean hasMatches() {
		return matches != null && !matches.isEmpty();
	}

	/**
	 * Gets the number of match patterns
	 * 
	 * @return Number of match patterns
	 */
	public int getMatchCount() {
		return matches != null ? matches.size() : 0;
	}

	/**
	 * Checks if a URL matches any of the patterns
	 * 
	 * @param url URL to check
	 * @return true if URL matches any pattern
	 */
	public boolean matchesUrl(String url) {
		if (url == null || matches == null || matches.isEmpty()) {
			return false;
		}

		for (String pattern : matches) {
			if (urlMatchesPattern(url, pattern)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Checks if URL matches a specific pattern (supports wildcards)
	 * 
	 * @param url     URL to check
	 * @param pattern Pattern with wildcards
	 * @return true if matches
	 */
	private boolean urlMatchesPattern(String url, String pattern) {
		if (url == null || pattern == null) {
			return false;
		}

		// Convert wildcard pattern to regex
		String regex = pattern
				.replace(".", "\\.")
				.replace("*", ".*")
				.replace("?", ".");

		return url.matches(regex);
	}

	/**
	 * Validates if the certificate configuration is valid
	 * 
	 * @return true if certificate is valid
	 */
	public boolean isValid() {
		// Must have a name
		if (name == null || name.trim().isEmpty()) {
			return false;
		}

		// Must have at least key or cert file
		if (!hasKey() && !hasCert()) {
			return false;
		}

		return true;
	}

	/**
	 * Gets the key file path
	 * 
	 * @return Key file path or null
	 */
	public String getKeyPath() {
		return key != null ? key.getSrc() : null;
	}

	/**
	 * Gets the cert file path
	 * 
	 * @return Cert file path or null
	 */
	public String getCertPath() {
		return cert != null ? cert.getSrc() : null;
	}

	/**
	 * Creates a copy of this Certificate
	 * 
	 * @return A new Certificate instance with the same values
	 */
	public Certificate copy() {
		Certificate.CertificateBuilder builder = Certificate.builder()
				.name(this.name)
				.passphrase(this.passphrase)
				.key(this.key != null ? this.key.copy() : null)
				.cert(this.cert != null ? this.cert.copy() : null);

		// Copy matches list
		if (this.matches != null) {
			builder.matches(new ArrayList<>(this.matches));
		}

		return builder.build();
	}

	/**
	 * Gets certificate summary
	 * 
	 * @return Certificate summary as string
	 */
	public String getSummary() {
		return String.format(
				"Certificate: %s\n" +
				"Has Key: %s\n" +
				"Has Cert: %s\n" +
				"Has Passphrase: %s\n" +
				"Matches: %d\n" +
				"Key Path: %s\n" +
				"Cert Path: %s",
				name != null ? name : "Unnamed",
				hasKey() ? "Yes" : "No",
				hasCert() ? "Yes" : "No",
				hasPassphrase() ? "Yes" : "No",
				getMatchCount(),
				getKeyPath() != null ? getKeyPath() : "Not set",
				getCertPath() != null ? getCertPath() : "Not set");
	}

	/**
	 * Removes key file
	 * 
	 * @return this instance for method chaining
	 */
	public Certificate withoutKey() {
		this.key = null;
		return this;
	}

	/**
	 * Removes cert file
	 * 
	 * @return this instance for method chaining
	 */
	public Certificate withoutCert() {
		this.cert = null;
		return this;
	}

	/**
	 * Removes passphrase
	 * 
	 * @return this instance for method chaining
	 */
	public Certificate withoutPassphrase() {
		this.passphrase = null;
		return this;
	}

	@Override
	public String toString() {
		return String.format("Certificate{name='%s', hasKey=%s, hasCert=%s, matches=%d}", 
				name, hasKey(), hasCert(), getMatchCount());
	}

	// Static utility methods

	/**
	 * Creates a P12/PKCS12 certificate
	 * 
	 * @param name       Certificate name
	 * @param p12Path    Path to P12 file
	 * @param passphrase P12 passphrase
	 * @return Certificate instance
	 */
	public static Certificate p12(String name, String p12Path, String passphrase) {
		return Certificate.builder()
				.name(name)
				.key(createFile(p12Path))
				.cert(createFile(p12Path))
				.passphrase(passphrase)
				.build();
	}

	/**
	 * Creates a PEM certificate
	 * 
	 * @param name     Certificate name
	 * @param keyPath  Path to PEM key file
	 * @param certPath Path to PEM cert file
	 * @return Certificate instance
	 */
	public static Certificate pem(String name, String keyPath, String certPath) {
		return Certificate.builder()
				.name(name)
				.key(createFile(keyPath))
				.cert(createFile(certPath))
				.build();
	}

	/**
	 * Validates a list of certificates
	 * 
	 * @param certificates List of certificates to validate
	 * @return List of validation errors
	 */
	public static List<String> validateCertificates(List<Certificate> certificates) {
		List<String> errors = new ArrayList<>();

		if (certificates == null) {
			errors.add("Certificates list is null");
			return errors;
		}

		for (int i = 0; i < certificates.size(); i++) {
			Certificate cert = certificates.get(i);
			if (cert == null) {
				errors.add("Certificate at index " + i + " is null");
				continue;
			}

			if (!cert.isValid()) {
				errors.add("Certificate at index " + i + " is invalid: " + cert.toString());
			}
		}

		return errors;
	}
}