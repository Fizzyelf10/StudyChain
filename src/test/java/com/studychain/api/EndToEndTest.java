package com.studychain.api;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Starts the whole application (real web server, in-memory database, mock escrow) and walks through
 * the main story over real HTTP: sell a PDF, buy it through escrow, then get a listing reported away.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "studychain.storage.directory=target/test-files")
class EndToEndTest {

    @Value("${local.server.port}")
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    // ---------- tiny helpers so the test reads like the story ----------

    private HttpResponse<String> send(String method, String path, String token, String json) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (json != null) builder.header("Content-Type", "application/json");
        builder.method(method, json == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json));
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String extract(String json, String field) {
        Matcher m = Pattern.compile("\"" + field + "\":\"([^\"]+)\"").matcher(json);
        assertTrue(m.find(), "field " + field + " not found in " + json);
        return m.group(1);
    }

    /** Registers a user, links a demo wallet, and returns the login token. */
    private String newUser(String name) throws Exception {
        String email = name + "-" + UUID.randomUUID() + "@example.com";
        HttpResponse<String> reg = send("POST", "/api/auth/register", null,
                "{\"email\":\"" + email + "\",\"password\":\"password123\",\"displayName\":\"" + name + "\"}");
        assertEquals(201, reg.statusCode(), reg.body());
        String token = extract(reg.body(), "token");
        // a random 20-byte address, different for every user (wallets must be unique)
        String wallet = "0x" + (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "").substring(0, 40);
        assertEquals(200, send("POST", "/api/dev/wallet", token, "{\"address\":\"" + wallet + "\"}").statusCode());
        return token;
    }

    private static byte[] randomPdf() throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.addPage(new PDPage());
            doc.getDocumentInformation().setTitle(UUID.randomUUID().toString()); // makes the bytes unique
            doc.save(out);
            return out.toByteArray();
        }
    }

    private HttpResponse<String> upload(String token, String metaJson, byte[] pdf) throws Exception {
        String boundary = "----test" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"meta\"\r\n"
                + "Content-Type: application/json\r\n\r\n" + metaJson + "\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"paper.pdf\"\r\n"
                + "Content-Type: application/pdf\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(pdf);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/listings"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String meta(String title) {
        return "{\"title\":\"" + title + "\",\"courseCode\":\"math101\",\"university\":\"Test University\","
                + "\"kind\":\"PAST_PAPER\",\"source\":\"UNIVERSITY_PUBLISHED\","
                + "\"sourceNote\":\"Published by the department for students.\",\"priceUsdc\":2.00,"
                + "\"ownershipConfirmed\":true}";
    }

    // ---------- the tests ----------

    @Test
    void healthEndpointIsPublic() throws Exception {
        HttpResponse<String> res = send("GET", "/api/health", null, null);
        assertEquals(200, res.statusCode());
        assertTrue(res.body().contains("ok"));
    }

    @Test
    void protectedEndpointsRejectAnonymousCallers() throws Exception {
        assertEquals(401, send("POST", "/api/orders", null, "{\"listingId\":\"" + UUID.randomUUID() + "\"}").statusCode());
        assertEquals(401, send("GET", "/api/orders/purchases", null, null).statusCode());
    }

    @Test
    void invalidListingDataIsRejectedWithFieldErrors() throws Exception {
        String seller = newUser("seller");
        HttpResponse<String> res = upload(seller, "{\"title\":\"\",\"priceUsdc\":0.01}", randomPdf());
        assertEquals(400, res.statusCode(), res.body());
        assertTrue(res.body().contains("fieldErrors"));
    }

    @Test
    void sellerListsAFileAndBuyerPaysThroughEscrow() throws Exception {
        String seller = newUser("seller");
        String buyer = newUser("buyer");
        String title = "Linear Algebra exam " + UUID.randomUUID();

        // seller lists a PDF
        HttpResponse<String> created = upload(seller, meta(title), randomPdf());
        assertEquals(201, created.statusCode(), created.body());
        String listingId = extract(created.body(), "id");
        assertTrue(created.body().contains("\"courseCode\":\"MATH101\""), "course code is upper-cased");

        // it shows up in public search
        HttpResponse<String> search = send("GET", "/api/listings?q=" + title.replace(" ", "%20"), null, null);
        assertEquals(200, search.statusCode());
        assertTrue(search.body().contains(listingId));

        // buyer cannot download before paying
        assertEquals(403, send("GET", "/api/listings/" + listingId + "/download", buyer, null).statusCode());

        // buyer creates an order and locks the payment in the (mock) escrow
        HttpResponse<String> order = send("POST", "/api/orders", buyer, "{\"listingId\":\"" + listingId + "\"}");
        assertEquals(200, order.statusCode(), order.body());
        String orderId = extract(order.body(), "id");
        assertTrue(order.body().contains("AWAITING_PAYMENT"));

        assertEquals(204, send("POST", "/api/dev/escrow/" + orderId + "/fund", buyer, null).statusCode());
        HttpResponse<String> funded = send("POST", "/api/orders/" + orderId + "/sync", buyer, null);
        assertTrue(funded.body().contains("\"status\":\"FUNDED\""), funded.body());

        // now the buyer can download a real PDF
        HttpResponse<String> download = send("GET", "/api/listings/" + listingId + "/download", buyer, null);
        assertEquals(200, download.statusCode());
        assertTrue(download.body().startsWith("%PDF-"));

        // the seller cannot release the buyer's money, only the buyer can
        assertEquals(403, send("POST", "/api/dev/escrow/" + orderId + "/release", seller, null).statusCode());
        assertEquals(204, send("POST", "/api/dev/escrow/" + orderId + "/release", buyer, null).statusCode());
        HttpResponse<String> done = send("POST", "/api/orders/" + orderId + "/sync", seller, null);
        assertTrue(done.body().contains("\"status\":\"COMPLETED\""), done.body());

        // buying the same file twice is refused
        assertEquals(409, send("POST", "/api/orders", buyer, "{\"listingId\":\"" + listingId + "\"}").statusCode());
    }

    @Test
    void theSameFileCannotBeListedTwice() throws Exception {
        String seller = newUser("seller");
        byte[] pdf = randomPdf();
        assertEquals(201, upload(seller, meta("First upload " + UUID.randomUUID()), pdf).statusCode());
        assertEquals(409, upload(seller, meta("Second upload " + UUID.randomUUID()), pdf).statusCode());
    }

    @Test
    void nonPdfUploadsAreRejected() throws Exception {
        String seller = newUser("seller");
        HttpResponse<String> res = upload(seller, meta("Not a pdf " + UUID.randomUUID()),
                "this is just text".getBytes(StandardCharsets.UTF_8));
        assertEquals(400, res.statusCode(), res.body());
    }

    @Test
    void threeReportsHideAListingFromSearch() throws Exception {
        String seller = newUser("seller");
        String title = "Reported paper " + UUID.randomUUID();
        String listingId = extract(upload(seller, meta(title), randomPdf()).body(), "id");

        for (int i = 0; i < 3; i++) {
            String reporter = newUser("reporter" + i);
            HttpResponse<String> res = send("POST", "/api/listings/" + listingId + "/reports", reporter,
                    "{\"reason\":\"LEAKED_EXAM\",\"details\":\"looks leaked\"}");
            assertEquals(201, res.statusCode(), res.body());
            // the same person cannot report twice
            assertEquals(409, send("POST", "/api/listings/" + listingId + "/reports", reporter,
                    "{\"reason\":\"OTHER\"}").statusCode());
        }

        HttpResponse<String> search = send("GET", "/api/listings?q=" + title.replace(" ", "%20"), null, null);
        assertFalse(search.body().contains(listingId), "listing should be hidden after 3 reports");
        // the seller still sees it, marked as under review
        HttpResponse<String> own = send("GET", "/api/listings/" + listingId, seller, null);
        assertTrue(own.body().contains("UNDER_REVIEW"), own.body());
        // anonymous visitors get 404
        assertEquals(404, send("GET", "/api/listings/" + listingId, null, null).statusCode());
    }
}
