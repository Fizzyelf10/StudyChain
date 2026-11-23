package com.studychain.api.storage;

/** What we learned about an uploaded PDF while storing it. */
public record StoredFile(String key, String originalName, long sizeBytes, String sha256, int pageCount,
                         boolean hasPreview) {
}
