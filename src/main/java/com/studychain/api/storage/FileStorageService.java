package com.studychain.api.storage;

import com.studychain.api.common.ApiException;
import com.studychain.api.config.AppProperties;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Stores uploaded PDFs on disk under random names (never the user's file name, which prevents path
 * tricks) and makes a small preview image of page 1. Swap this class for S3 later without touching
 * the rest of the app.
 */
@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    private final Path pdfDir;
    private final Path previewDir;

    public FileStorageService(AppProperties props) throws IOException {
        Path root = Path.of(props.storage().directory()).toAbsolutePath().normalize();
        this.pdfDir = root.resolve("pdf");
        this.previewDir = root.resolve("preview");
        Files.createDirectories(pdfDir);
        Files.createDirectories(previewDir);
    }

    public StoredFile store(MultipartFile upload) {
        if (upload == null || upload.isEmpty()) {
            throw ApiException.badRequest("Choose a PDF file to upload");
        }
        String originalName = cleanName(upload.getOriginalFilename());
        if (!originalName.toLowerCase().endsWith(".pdf")) {
            throw ApiException.badRequest("Only PDF files are accepted");
        }

        String key = UUID.randomUUID().toString();
        Path target = pdfDir.resolve(key + ".pdf");
        try {
            upload.transferTo(target);
            requirePdfSignature(target);
            String sha256 = sha256(target);

            int pages;
            boolean hasPreview = false;
            try (PDDocument document = Loader.loadPDF(target.toFile())) {
                pages = document.getNumberOfPages();
                if (pages < 1) {
                    throw ApiException.badRequest("The PDF has no pages");
                }
                hasPreview = renderPreview(document, previewDir.resolve(key + ".png"));
            }
            return new StoredFile(key, originalName, Files.size(target), sha256, pages, hasPreview);
        } catch (IOException e) {
            delete(key);
            throw ApiException.badRequest("That file is not a readable PDF (encrypted or damaged files are rejected)");
        } catch (RuntimeException e) {
            delete(key);
            throw e;
        }
    }

    public Resource pdf(String key) {
        return new FileSystemResource(pdfDir.resolve(key + ".pdf"));
    }

    public Optional<Resource> preview(String key) {
        Path path = previewDir.resolve(key + ".png");
        return Files.exists(path) ? Optional.of(new FileSystemResource(path)) : Optional.empty();
    }

    public void delete(String key) {
        try {
            Files.deleteIfExists(pdfDir.resolve(key + ".pdf"));
            Files.deleteIfExists(previewDir.resolve(key + ".png"));
        } catch (IOException e) {
            log.warn("Could not delete files for {}", key, e);
        }
    }

    /** A real PDF starts with the five characters "%PDF-". The file name alone proves nothing. */
    private static void requirePdfSignature(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            byte[] head = in.readNBytes(5);
            if (!"%PDF-".equals(new String(head, StandardCharsets.US_ASCII))) {
                throw new IOException("missing PDF signature");
            }
        }
    }

    private static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e); // SHA-256 exists on every JVM
        }
    }

    /** A failed preview must not fail the upload, so errors are logged and swallowed. */
    private static boolean renderPreview(PDDocument document, Path output) {
        try {
            BufferedImage image = new PDFRenderer(document).renderImageWithDPI(0, 60);
            return ImageIO.write(image, "png", output.toFile());
        } catch (Exception e) {
            log.warn("Could not render preview: {}", e.getMessage());
            return false;
        }
    }

    private static String cleanName(String name) {
        if (name == null || name.isBlank()) {
            return "document.pdf";
        }
        String base = name.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1).replaceAll("\\p{Cntrl}", "").trim();
        if (base.isEmpty()) {
            return "document.pdf";
        }
        return base.length() > 255 ? base.substring(base.length() - 255) : base;
    }
}
