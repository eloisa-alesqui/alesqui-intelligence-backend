package es.alesqui.intelligence.controller;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Controller to handle file download requests.
 * It provides an endpoint to securely download files stored in a temporary directory.
 */
@Controller
@RequestMapping("/api/files")
public class FileDownloadController {

    private final Path tempFileDir = Paths.get(System.getProperty("java.io.tmpdir"), "alesqui-intelligence-files");

    /**
     * Handles the download of a specific file from the temporary directory.
     *
     * @param filename The name of the file to be downloaded.
     * @return A ResponseEntity containing the file resource for download,
     * or an appropriate error response (e.g., not found, bad request, internal server error).
     */
    @GetMapping("/download/{filename:.+}")
    @PreAuthorize("hasAnyRole('ROLE_IT', 'ROLE_BUSINESS')")
    public ResponseEntity<Resource> downloadFile(@PathVariable String filename) {
        try {
            Path filePath = tempFileDir.resolve(filename).normalize();

            // Important security measure to prevent Path Traversal attacks
            if (!filePath.startsWith(tempFileDir)) {
                return ResponseEntity.badRequest().build();
            }

            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() && resource.isReadable()) {
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}