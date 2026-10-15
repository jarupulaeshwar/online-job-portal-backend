package com.jobportal.controller;

import com.jobportal.service.ResumeFile;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;

/** Builds the HTTP response used to send a PDF back to the browser. */
final class ResumeResponses {
    private ResumeResponses() {}

    static ResponseEntity<byte[]> pdf(ResumeFile file) {
        String name = file.originalName() == null ? "resume.pdf" : file.originalName();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(name, StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(file.data());
    }
}
