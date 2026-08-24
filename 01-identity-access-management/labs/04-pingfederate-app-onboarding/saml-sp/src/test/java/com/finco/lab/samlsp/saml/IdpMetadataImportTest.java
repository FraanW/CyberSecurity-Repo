package com.finco.lab.samlsp.saml;

import com.finco.lab.samlsp.SamlTestFixtures;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The feature under test: <b>upload the IdP's metadata and the SP is configured, live, with no
 * redeploy</b> — and, just as importantly, nobody who is not logged in can do it.
 *
 * <p>Ordered, because these steps are a story: preview changes nothing, import changes everything,
 * reset puts it back.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IdpMetadataImportTest {

    private static final String ENTITY_ID = "https://pf.lab.invalid/idp";
    private static final String SSO_URL = "https://pf.lab.invalid/idp/SSO.saml2";

    @TempDir
    static Path stateDir;

    /** Never let a test write into the default state directory — it would leak into other tests. */
    @DynamicPropertySource
    static void isolateState(DynamicPropertyRegistry registry) {
        registry.add("lab.saml.state-dir", () -> stateDir.toString());
    }

    @Autowired
    MockMvc mvc;

    private static final SamlTestFixtures.Idp SIGNER = SamlTestFixtures.idp("pingfed-signing");

    private static MockMultipartFile metadataFile() {
        return new MockMultipartFile("file", "idp-metadata.xml", "application/xml",
                SamlTestFixtures.metadata(ENTITY_ID, SSO_URL, SIGNER).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @Order(1)
    void anonymousCallersCannotChangeWhoThisAppTrusts() throws Exception {
        // The whole security argument for the feature: an unauthenticated import would let anyone
        // re-point this SP at an IdP they control and then log in as anybody.
        mvc.perform(multipart("/api/idp").file(metadataFile()).with(csrf()))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/idp/metadata").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(2)
    void previewDescribesTheFileAndChangesNothing() throws Exception {
        mvc.perform(multipart("/api/idp/metadata/preview").file(metadataFile())
                        .with(user("farhaan")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applied").value(false))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.identityProviders[0].entityId").value(ENTITY_ID))
                .andExpect(jsonPath("$.identityProviders[0].singleSignOnServiceLocation").value(SSO_URL))
                .andExpect(jsonPath("$.identityProviders[0].signingCertificates[0].sha256Fingerprint")
                        .value(CertificateSummary.of(SIGNER.certificate()).sha256Fingerprint()))
                .andExpect(jsonPath("$.environmentVariables.LAB_SAML_IDP_ENTITY_ID").value(ENTITY_ID));

        // Still unconfigured: preview must not have touched the live registration.
        mvc.perform(get("/api/config"))
                .andExpect(jsonPath("$.configured").value(false));
    }

    @Test
    @Order(3)
    void garbageIsRejectedWithAReadableMessage() throws Exception {
        MockMultipartFile junk = new MockMultipartFile("file", "notes.txt", "text/plain",
                "this is not metadata".getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/idp/metadata/preview").file(junk)
                        .with(user("farhaan")).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_metadata"));
    }

    @Test
    @Order(4)
    void spMetadataIsNotAcceptedInPlaceOfIdpMetadata() throws Exception {
        // A genuinely common mistake: exporting this app's own metadata and uploading that back.
        String spMetadata = mvc.perform(get("/api/sp-metadata.xml"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        MockMultipartFile wrongWayRound = new MockMultipartFile("file", "sp-metadata.xml",
                "application/xml", spMetadata.getBytes(StandardCharsets.UTF_8));

        mvc.perform(multipart("/api/idp/metadata/preview").file(wrongWayRound)
                        .with(user("farhaan")).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("IDPSSODescriptor")));
    }

    @Test
    @Order(5)
    void importingConfiguresTheSpImmediately() throws Exception {
        mvc.perform(multipart("/api/idp").file(metadataFile())
                        .with(user("farhaan")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applied").value(true))
                .andExpect(jsonPath("$.state.source").value("IMPORTED_METADATA"))
                .andExpect(jsonPath("$.state.trustAnchorIsPlaceholder").value(false));

        // No restart, no redeploy: the live registration is already the new one.
        mvc.perform(get("/api/config"))
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.identityProvider.entityId").value(ENTITY_ID))
                .andExpect(jsonPath("$.identityProvider.singleSignOnServiceUrl").value(SSO_URL))
                .andExpect(jsonPath("$.identityProvider.trustedSigningCertificates[0].sha256Fingerprint")
                        .value(CertificateSummary.of(SIGNER.certificate()).sha256Fingerprint()));

        // And it is readable without a login, because none of it is secret.
        mvc.perform(get("/api/idp"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idp.entityId").value(ENTITY_ID));
    }

    @Test
    @Order(6)
    void aRotationDocumentWithTwoCertificatesTrustsBoth() throws Exception {
        SamlTestFixtures.Idp second = SamlTestFixtures.idp("pingfed-signing-next");
        MockMultipartFile rotating = new MockMultipartFile("file", "idp-metadata.xml", "application/xml",
                SamlTestFixtures.metadata(ENTITY_ID, SSO_URL, SIGNER, second).getBytes(StandardCharsets.UTF_8));

        mvc.perform(multipart("/api/idp").file(rotating).with(user("farhaan")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state.idp.signingCertificates.length()").value(2));

        mvc.perform(get("/api/config"))
                .andExpect(jsonPath("$.identityProvider.trustedSigningCertificates.length()").value(2));
    }

    @Test
    @Order(7)
    void resetGoesBackToTheEnvironmentConfiguration() throws Exception {
        mvc.perform(delete("/api/idp/metadata").with(user("farhaan")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state.source").value("NONE"))
                .andExpect(jsonPath("$.state.trustAnchorIsPlaceholder").value(true));

        mvc.perform(get("/api/config"))
                .andExpect(jsonPath("$.configured").value(false));
    }
}
