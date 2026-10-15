package com.jobportal.service;

import com.jobportal.config.AppProperties;
import com.jobportal.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Stores resumes on disk (outside the web root) under random names.
 * Only the random name and the original file name are saved in MySQL.
 */
@Service
public class FileStorageService {
    private static final Pattern STORED_NAME = Pattern.compile("^[a-f0-9\\-]{36}\\.pdf$");

    private final Path root;
    private final long maxBytes;

    public FileStorageService(AppProperties props) throws IOException {
        this.root = Paths.get(props.storage().uploadDir()).toAbsolutePath().normalize().resolve("resumes");
        this.maxBytes = props.storage().maxResumeBytes();
        Files.createDirectories(root);
    }

    public record StoredFile(String storedName, String originalName, long size) {}

    public StoredFile storeResume(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Please choose a PDF file to upload.");
        }
        if (file.getSize() > maxBytes) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "Resume is too large. The maximum size is " + (maxBytes / (1024 * 1024)) + " MB.");
        }
        String original = StringUtils.getFilename(StringUtils.cleanPath(
                file.getOriginalFilename() == null ? "" : file.getOriginalFilename()));
        if (original == null || !original.toLowerCase().endsWith(".pdf")) {
            throw ApiException.badRequest("Only PDF files are allowed (the file name must end with .pdf).");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.equalsIgnoreCase("application/pdf")) {
            throw ApiException.badRequest("Only PDF files are allowed.");
        }
        try (InputStream in = file.getInputStream()) {
            byte[] header = in.readNBytes(5);
            if (!"%PDF-".equals(new String(header, StandardCharsets.ISO_8859_1))) {
                throw ApiException.badRequest("This file is not a valid PDF document.");
            }
        } catch (IOException ex) {
            throw ApiException.badRequest("The uploaded file could not be read.");
        }
        String storedName = UUID.randomUUID() + ".pdf";
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, root.resolve(storedName), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "The resume could not be saved. Please try again.");
        }
        if (original.length() > 200) {
            original = original.substring(original.length() - 200);
        }
        return new StoredFile(storedName, original, file.getSize());
    }

    public byte[] read(String storedName) {
        if (storedName == null || !STORED_NAME.matcher(storedName).matches()) {
            throw ApiException.notFound("Resume not found.");
        }
        Path path = root.resolve(storedName).normalize();
        if (!path.startsWith(root) || !Files.isRegularFile(path)) {
            throw ApiException.notFound("Resume file is missing on the server.");
        }
        try {
            return Files.readAllBytes(path);
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "The resume could not be read.");
        }
    }
}
