package es.alesqui.intelligence.controller;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * REST controller for handling secure file download operations.
 * 
 * This controller provides endpoints for downloading files from a temporary
 * directory with built-in security measures and reactive streaming
 * capabilities. It handles the complexities of Spring WebFlux's reactive
 * nature, including multiple execution scenarios and read-only header states.
 * 
 * Key features: 
 * - Path traversal attack prevention 
 * - Asynchronous file streaming with backpressure support 
 * - Automatic content type detection 
 * - Graceful handling of WebFlux multiple executions 
 * - Comprehensive error handling and logging
 * 
 * Security considerations: 
 * - All file paths are normalized and validated against the base directory 
 * - File existence and readability checks are performed before streaming 
 * - Proper HTTP status codes are returned for different error scenarios
 */
@Controller
@RequestMapping("/api/files")
@Slf4j
public class FileDownloadController {

	/**
	 * Base directory for temporary file storage. Files are stored in the system's
	 * temporary directory under a subdirectory specific to this application to
	 * avoid conflicts with other applications.
	 */
	private final Path tempFileDir = Paths.get(System.getProperty("java.io.tmpdir"), "alesqui-intelligence-files");

	/**
	 * Data buffer factory for creating reactive data buffers. Uses default
	 * configuration which is suitable for most file streaming scenarios. Buffer
	 * size is optimized for memory usage vs. performance balance.
	 */
	private final DefaultDataBufferFactory bufferFactory = new DefaultDataBufferFactory();

	/**
	 * Buffer size for file reading operations in bytes. 8KB provides a good balance
	 * between memory usage and I/O efficiency. This size aligns well with typical
	 * filesystem block sizes and network MTU.
	 */
	private static final int BUFFER_SIZE = 8192;

	/**
	 * Downloads a file from the temporary directory with streaming support.
	 * 
	 * This endpoint handles file downloads with the following features: 
	 * - Secure path validation to prevent directory traversal attacks 
	 * - Reactive streaming for efficient memory usage with large files 
	 * - Automatic content type detection based on file extension 
	 * - Proper HTTP headers for file download (Content-Disposition, Content-Type, Content-Length) 
	 * - Graceful handling of WebFlux multiple execution scenarios
	 * 
	 * The method is designed to handle Spring WebFlux's reactive nature where the
	 * same request might be processed multiple times. Only the first execution with
	 * mutable headers will perform the actual file streaming.
	 * 
	 * @param filename The name of the file to download. Must not contain path
	 *                 separators or relative path components to prevent directory
	 *                 traversal attacks. The filename should match exactly with a
	 *                 file in the temp directory.
	 * @param exchange The server web exchange containing request/response
	 *                 information. Used to access response headers and write the
	 *                 file stream.
	 * @return A Mono that completes when the file has been fully streamed to the
	 *         client, or immediately if this is a subsequent execution with
	 *         read-only headers. Returns error responses for invalid requests or
	 *         file access issues.
	 */
	@GetMapping("/download/{filename:.+}")
	public Mono<Void> downloadFile(@PathVariable String filename, ServerWebExchange exchange) {

		log.debug("Processing file download request for: {}", filename);

		try {
			// Resolve and normalize the file path to prevent directory traversal attacks
			// The normalize() call removes any ".." or "." components from the path
			Path filePath = this.tempFileDir.resolve(filename).normalize();

			// Security validation: Ensure the resolved path is still within our temp
			// directory
			// This prevents attacks like "../../../etc/passwd" from accessing system files
			if (!filePath.startsWith(this.tempFileDir)) {
				log.warn("Path traversal attempt detected for file: {}", filename);
				return writeErrorResponse(exchange.getResponse(), HttpStatus.BAD_REQUEST);
			}

			// Check if the requested file exists in the filesystem
			if (!Files.exists(filePath)) {
				log.debug("File not found: {}", filename);
				return writeErrorResponse(exchange.getResponse(), HttpStatus.NOT_FOUND);
			}

			// Verify that the file is readable (permissions check)
			// This can fail if file permissions have changed or file is locked
			if (!Files.isReadable(filePath)) {
				log.error("File exists but is not readable: {}", filename);
				return writeErrorResponse(exchange.getResponse(), HttpStatus.INTERNAL_SERVER_ERROR);
			}

			log.debug("Successfully serving file: {}", filename);

			// Attempt to set response headers and stream the file
			// This handles the WebFlux multiple execution scenario gracefully
			return setHeadersAndStreamFile(filename, filePath, exchange.getResponse());

		} catch (Exception e) {
			// Catch-all for any unexpected exceptions during file processing
			log.error("Unexpected error processing file download for: {}", filename, e);
			return writeErrorResponse(exchange.getResponse(), HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Sets HTTP response headers and initiates file streaming.
	 * 
	 * This method handles the complexity of Spring WebFlux's reactive execution
	 * model where the same request might be processed multiple times. In subsequent
	 * executions, the response headers become read-only, and attempting to modify
	 * them throws an UnsupportedOperationException.
	 * 
	 * The method uses a try-catch approach to detect read-only headers: 
	 * - First execution: Headers are mutable, streaming proceeds normally 
	 * - Subsequent executions: Headers are read-only, operation is skipped silently
	 * 
	 * @param filename The name of the file being downloaded (for logging purposes)
	 * @param filePath The resolved path to the file on the filesystem
	 * @param response The HTTP response object to write headers and stream content
	 *                 to
	 * @return A Mono that completes when streaming is finished, or empty if headers
	 *         are read-only
	 */
	private Mono<Void> setHeadersAndStreamFile(String filename, Path filePath, ServerHttpResponse response) {
		try {
			// Attempt to set HTTP headers for the file download
			HttpHeaders headers = response.getHeaders();

			// Set Content-Disposition header to trigger download in browsers
			// The "attachment" directive forces download rather than inline display
			headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");

			// Set appropriate Content-Type based on file extension
			// Falls back to application/octet-stream if type cannot be determined
			headers.add(HttpHeaders.CONTENT_TYPE, determineContentType(filePath).toString());

			// Attempt to set Content-Length header for better client experience
			// This allows browsers to show download progress and remaining time
			try {
				long fileSize = Files.size(filePath);
				headers.add(HttpHeaders.CONTENT_LENGTH, String.valueOf(fileSize));
			} catch (IOException e) {
				// File size determination failed, but this is not critical
				// The download will still work without Content-Length header
				log.debug("Could not determine file size for: {}", filename, e);
			}

			// If we reach this point, headers were set successfully
			// Proceed with file streaming
			return streamFile(filePath, response);

		} catch (UnsupportedOperationException e) {
			// Headers are read-only, indicating this is a subsequent WebFlux execution
			// This is expected behavior and should be handled silently
			log.debug("Headers are read-only, skipping file streaming for: {}", filename);
			return Mono.empty();
		}
	}

	/**
	 * Streams file content to the HTTP response using reactive streams.
	 * 
	 * This method implements efficient file streaming using Spring's reactive
	 * DataBufferUtils with asynchronous file channels. The approach provides: -
	 * Non-blocking I/O operations - Automatic backpressure handling -
	 * Memory-efficient streaming for large files - Proper resource cleanup
	 * 
	 * The streaming process: 
	 * 1. Opens an asynchronous file channel for non-blocking reads 
	 * 2. Creates a reactive stream of data buffers from the file 
	 * 3. Writes the stream to the HTTP response 
	 * 4. Ensures proper cleanup of file resources
	 * 
	 * @param filePath The path to the file to be streamed
	 * @param response The HTTP response to write the file content to
	 * @return A Mono that completes when the entire file has been streamed, or
	 *         fails if file I/O errors occur
	 */
	private Mono<Void> streamFile(Path filePath, ServerHttpResponse response) {
		try {
			// Open asynchronous file channel for non-blocking file operations
			// READ mode is sufficient as we only need to read file content
			AsynchronousFileChannel channel = AsynchronousFileChannel.open(filePath, StandardOpenOption.READ);

			// Create reactive flux of data buffers from the file channel
			// The buffer factory and size are optimized for memory efficiency
			Flux<DataBuffer> dataBufferFlux = DataBufferUtils.readAsynchronousFileChannel(() -> channel, bufferFactory,
					BUFFER_SIZE);

			// Write the data buffer flux to the HTTP response
			// The doFinally ensures proper resource cleanup regardless of completion status
			return response.writeWith(dataBufferFlux).doFinally(signalType -> {
				// Clean up file channel resources
				// This is critical to prevent file handle leaks
				try {
					channel.close();
				} catch (IOException e) {
					// Log but don't fail the operation for cleanup issues
					log.debug("Error closing file channel", e);
				}
			});

		} catch (IOException e) {
			// File channel opening failed - this indicates serious I/O issues
			log.error("Error opening file channel for: {}", filePath, e);
			return writeErrorResponse(response, HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Writes an HTTP error response with the specified status code.
	 * 
	 * This method handles error response generation while being aware of the
	 * WebFlux multiple execution scenario. If headers are already read-only, the
	 * method fails silently rather than throwing exceptions.
	 * 
	 * @param response The HTTP response object to write the error to
	 * @param status   The HTTP status code to set for the error response
	 * @return A Mono that completes when the error response is written, or empty if
	 *         the response is already committed
	 */
	private Mono<Void> writeErrorResponse(ServerHttpResponse response, HttpStatus status) {
		try {
			// Attempt to set the HTTP status code for the error response
			response.setStatusCode(status);
			// Complete the response without body content
			return response.setComplete();
		} catch (Exception e) {
			// Response is likely already committed or headers are read-only
			// This can happen in subsequent WebFlux executions
			log.debug("Could not set error response status (headers read-only)", e);
			return Mono.empty();
		}
	}

	/**
	 * Determines the appropriate MIME content type for a file.
	 * 
	 * This method uses Java's built-in content type detection mechanism which
	 * examines file extensions and, on some systems, file content to determine the
	 * most appropriate MIME type.
	 * 
	 * The content type affects how browsers handle the downloaded file: - Correct
	 * types enable proper application association - Generic types
	 * (application/octet-stream) trigger generic download
	 * 
	 * @param filePath The path to the file for which to determine content type
	 * @return The MediaType representing the file's content type, defaults to
	 *         APPLICATION_OCTET_STREAM if detection fails
	 */
	private MediaType determineContentType(Path filePath) {
		try {
			// Use Java's built-in content type detection
			// This checks file extensions and may examine file content
			String contentType = Files.probeContentType(filePath);
			if (contentType != null) {
				return MediaType.parseMediaType(contentType);
			}
		} catch (IOException e) {
			// Content type detection failed, but this is not critical
			log.debug("Could not determine content type for file: {}", filePath, e);
		}

		// Fall back to generic binary content type
		// This ensures browsers will still handle the download properly
		return MediaType.APPLICATION_OCTET_STREAM;
	}
}