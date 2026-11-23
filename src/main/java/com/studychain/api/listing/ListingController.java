package com.studychain.api.listing;

import com.studychain.api.common.ApiException;
import com.studychain.api.common.PageResponse;
import com.studychain.api.listing.ListingDtos.CreateListingRequest;
import com.studychain.api.listing.ListingDtos.ListingResponse;
import com.studychain.api.security.AuthUser;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/listings")
public class ListingController {

    private final ListingService service;

    public ListingController(ListingService service) {
        this.service = service;
    }

    /** GET /api/listings?q=algebra&kind=PAST_PAPER&page=0&size=12 */
    @GetMapping
    public PageResponse<ListingResponse> search(@RequestParam(defaultValue = "") String q,
                                                @RequestParam(required = false) ListingKind kind,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "12") int size) {
        return service.search(q, kind, page, size);
    }

    @GetMapping("/mine")
    public PageResponse<ListingResponse> mine(@AuthenticationPrincipal AuthUser user,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "50") int size) {
        return service.mine(user, page, size);
    }

    @GetMapping("/{id}")
    public ListingResponse get(@PathVariable UUID id, @AuthenticationPrincipal AuthUser viewer) {
        return service.get(id, viewer);
    }

    /**
     * Multipart upload with two parts: "meta" (JSON with the listing details) and "file" (the PDF).
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ListingResponse create(@AuthenticationPrincipal AuthUser user,
                                  @Valid @RequestPart("meta") CreateListingRequest meta,
                                  @RequestPart("file") MultipartFile file) {
        return service.create(user, meta, file);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable UUID id, @AuthenticationPrincipal AuthUser user) {
        service.remove(id, user);
    }

    @GetMapping("/{id}/preview")
    public ResponseEntity<Resource> preview(@PathVariable UUID id) {
        Resource image = service.preview(id).orElseThrow(() -> ApiException.notFound("No preview available"));
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS))
                .body(image);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable UUID id, @AuthenticationPrincipal AuthUser user) {
        ListingService.DownloadFile file = service.download(id, user);
        String disposition = ContentDisposition.attachment()
                .filename(file.filename(), StandardCharsets.UTF_8).build().toString();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .body(file.resource());
    }
}
