package com.finco.lab.samlsp.web;

import com.finco.lab.samlsp.saml.IdpMetadata;
import com.finco.lab.samlsp.saml.IdpTrustStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Import an IdP's SAML metadata and configure this SP from it — at runtime, no redeploy.
 *
 * <h2>Why a metadata file beats three environment variables</h2>
 *
 * <p>Entity ID, SSO URL and signing certificate are the three things an SP must know about its
 * IdP, and every one of them is a chance to make a mistake by hand: a URL with a trailing slash,
 * a PEM whose newlines were eaten by a settings form, a certificate that was correct last quarter.
 * SAML metadata is those three facts in a machine-readable document that the IdP itself
 * generated — so importing it removes the transcription step, and with it the class of failure
 * you are looking at right now. That is not a convenience feature; it is the fix for
 * {@code invalid_signature}.</p>
 *
 * <h2>Two steps on purpose</h2>
 *
 * <p><b>Preview</b> parses and shows you what is inside — including the SHA-256 fingerprint of
 * every signing certificate — and changes nothing. <b>Import</b> commits. The gap between them is
 * where you check the fingerprint against the PingFederate console, which is the one act that
 * makes the import trustworthy. A metadata document is not self-authenticating: unless it is
 * XML-signed by a key you already trust, importing it is a decision to trust whoever handed you
 * the file.</p>
 *
 * <p><b>Every mutating endpoint here requires a session.</b> Changing which IdP an app trusts is
 * changing who can issue valid logins for it — an anonymous caller who could POST metadata could
 * point this SP at an IdP they control and walk in as anyone. Log in at Step 0 first.</p>
 */
@RestController
@RequestMapping("/api/idp")
public class IdpMetadataController {

    private static final Logger log = LoggerFactory.getLogger(IdpMetadataController.class);
    private static final Duration FETCH_TIMEOUT = Duration.ofSeconds(10);

    private final IdpTrustStore trustStore;

    public IdpMetadataController(IdpTrustStore trustStore) {
        this.trustStore = trustStore;
    }

    /** The live trust configuration: who we trust, where that came from, and what is wrong with it. */
    @GetMapping
    public IdpTrustStore.TrustState current() {
        return trustStore.state();
    }

    /**
     * Parse without applying. Accepts an uploaded file, pasted XML, or a URL to fetch.
     *
     * <p>The response includes the environment variables that would reproduce the configuration,
     * because an import survives a restart but not a redeploy — those variables are how you make
     * it permanent.</p>
     */
    @PostMapping(value = "/metadata/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> previewUpload(@RequestPart(value = "file", required = false) MultipartFile file,
                                             @RequestParam(value = "xml", required = false) String xml,
                                             @RequestParam(value = "url", required = false) String url)
            throws IOException {
        Payload payload = payload(file, xml, url);
        return preview(payload);
    }

    /** Same as above for callers that just POST the XML as the body — {@code curl --data-binary @idp.xml}. */
    @PostMapping(value = "/metadata/preview", consumes = {MediaType.APPLICATION_XML_VALUE, MediaType.TEXT_XML_VALUE})
    public Map<String, Object> previewBody(InputStream body) throws IOException {
        return preview(new Payload(body.readAllBytes(), "the request body"));
    }

    /**
     * Apply. Swaps the live registration atomically, so the very next click on "Log in with
     * PingFederate" uses the new trust anchors — no restart, no redeploy.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> importUpload(@RequestPart(value = "file", required = false) MultipartFile file,
                                            @RequestParam(value = "xml", required = false) String xml,
                                            @RequestParam(value = "url", required = false) String url,
                                            @RequestParam(value = "entityId", required = false) String entityId)
            throws IOException {
        Payload payload = payload(file, xml, url);
        return applied(trustStore.importMetadata(payload.bytes(), entityId, payload.origin()));
    }

    @PostMapping(consumes = {MediaType.APPLICATION_XML_VALUE, MediaType.TEXT_XML_VALUE})
    public Map<String, Object> importBody(InputStream body,
                                          @RequestParam(value = "entityId", required = false) String entityId)
            throws IOException {
        return applied(trustStore.importMetadata(body.readAllBytes(), entityId, "the request body"));
    }

    /** Forget the imported document and fall back to whatever the environment variables say. */
    @DeleteMapping("/metadata")
    public Map<String, Object> reset() {
        return applied(trustStore.reset());
    }

    // ------------------------------------------------------------------ helpers

    private Map<String, Object> preview(Payload payload) {
        List<IdpMetadata> parsed = trustStore.preview(payload.bytes());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("source", payload.origin());
        body.put("count", parsed.size());
        body.put("identityProviders", parsed);
        body.put("applied", false);
        body.put("environmentVariables", parsed.size() == 1
                ? parsed.get(0).asEnvironmentVariables()
                : Map.of());
        body.put("note", "Nothing has changed yet. Check the certificate fingerprints below against "
                + "the PingFederate console — SP Connection → Credentials → Digital Signature "
                + "Settings — then import. A metadata file only proves what it says if you know "
                + "who it came from.");
        return body;
    }

    private Map<String, Object> applied(IdpTrustStore.TrustState state) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("applied", true);
        body.put("state", state);
        body.put("environmentVariables", state.idp().asEnvironmentVariables());
        body.put("note", "Live now — the next login uses these values. This survives a restart but "
                + "not a redeploy: paste the environment variables above into your host to make "
                + "it permanent. The IdP signing certificate is not among them because importing "
                + "the metadata is the better way to supply it; use LAB_SAML_IDP_CERTIFICATE only "
                + "when you cannot reach the metadata at all.");
        return body;
    }

    private record Payload(byte[] bytes, String origin) {
    }

    private Payload payload(MultipartFile file, String xml, String url) throws IOException {
        if (file != null && !file.isEmpty()) {
            return new Payload(file.getBytes(), "uploaded file " + safeName(file.getOriginalFilename()));
        }
        if (xml != null && !xml.isBlank()) {
            return new Payload(xml.getBytes(StandardCharsets.UTF_8), "pasted XML");
        }
        if (url != null && !url.isBlank()) {
            return new Payload(fetch(url), url);
        }
        throw new IdpMetadata.ParseException("Send a metadata file, pasted XML, or a URL to fetch.");
    }

    /**
     * Fetch metadata over HTTP(S).
     *
     * <p><b>The honest caveat:</b> this makes the server issue a request to a URL a user chose,
     * which is the shape of an SSRF. It is gated behind a login and limited to http/https with a
     * short timeout and a size cap, which is proportionate for a lab. A real admin console would
     * also refuse private address ranges and cloud metadata endpoints. Uploading the file avoids
     * the question entirely, which is why the UI puts upload first.</p>
     */
    private byte[] fetch(String url) throws IOException {
        URI uri = URI.create(url.trim());
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if (!scheme.equals("https") && !scheme.equals("http")) {
            throw new IdpMetadata.ParseException("Only http:// and https:// URLs can be fetched.");
        }
        try (HttpClient client = HttpClient.newBuilder()
                .connectTimeout(FETCH_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build()) {
            HttpResponse<byte[]> response = client.send(
                    HttpRequest.newBuilder(uri).timeout(FETCH_TIMEOUT)
                            .header("Accept", "application/samlmetadata+xml, application/xml, text/xml, */*")
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() / 100 != 2) {
                throw new IdpMetadata.ParseException("Fetching " + url + " returned HTTP "
                        + response.statusCode() + ".");
            }
            byte[] body = response.body();
            if (body.length > IdpTrustStore.MAX_METADATA_BYTES) {
                throw new IdpMetadata.ParseException("That URL returned " + (body.length / 1024)
                        + " KB, which is far too large to be SAML metadata.");
            }
            return body;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IdpMetadata.ParseException("Interrupted while fetching " + url + ".");
        } catch (IdpMetadata.ParseException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IdpMetadata.ParseException("Could not fetch " + url + ": " + ex.getMessage()
                    + ". If PingFederate is not reachable from this server — a private network, or a "
                    + "certificate this JVM does not trust — download the file in your browser and "
                    + "upload it instead.", ex);
        }
    }

    /** Filenames come from the client; never echo one back into a response unfiltered. */
    private static String safeName(String name) {
        if (name == null || name.isBlank()) {
            return "(unnamed)";
        }
        String trimmed = name.length() > 80 ? name.substring(0, 80) : name;
        return trimmed.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    @ExceptionHandler({IdpMetadata.ParseException.class, IllegalArgumentException.class})
    public ResponseEntity<Map<String, Object>> badMetadata(RuntimeException ex) {
        log.warn("SAML SP: metadata import rejected — {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "invalid_metadata", "message", String.valueOf(ex.getMessage())));
    }
}
