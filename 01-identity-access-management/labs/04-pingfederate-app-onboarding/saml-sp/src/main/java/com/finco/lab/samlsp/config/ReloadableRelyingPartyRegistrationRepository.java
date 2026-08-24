package com.finco.lab.samlsp.config;

import org.springframework.security.saml2.provider.service.registration.IterableRelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A registration repository whose single registration can be swapped at runtime.
 *
 * <p><b>Why not {@code InMemoryRelyingPartyRegistrationRepository}?</b> Because it is immutable
 * by design, which is right for production — you do not want the set of IdPs an app trusts to be
 * mutable at runtime. This is a lab whose entire purpose is changing that configuration and
 * watching what happens, so it needs the opposite property. The volatile reference means an
 * in-flight login either sees the old registration or the new one, never a half-built object.</p>
 *
 * <p>Everything downstream — the AuthnRequest filter, the ACS filter, the metadata endpoint —
 * looks the registration up through this repository on every request, so a swap takes effect on
 * the next click with no restart.</p>
 */
public class ReloadableRelyingPartyRegistrationRepository implements IterableRelyingPartyRegistrationRepository {

    private final AtomicReference<RelyingPartyRegistration> current = new AtomicReference<>();

    public ReloadableRelyingPartyRegistrationRepository(RelyingPartyRegistration initial) {
        this.current.set(initial);
    }

    @Override
    public RelyingPartyRegistration findByRegistrationId(String registrationId) {
        RelyingPartyRegistration registration = current.get();
        return registration != null && registration.getRegistrationId().equals(registrationId)
                ? registration
                : null;
    }

    @Override
    public Iterator<RelyingPartyRegistration> iterator() {
        RelyingPartyRegistration registration = current.get();
        return (registration == null ? List.<RelyingPartyRegistration>of() : List.of(registration)).iterator();
    }

    /** Atomically replace the live registration. Returns the one that was there before. */
    public RelyingPartyRegistration replace(RelyingPartyRegistration registration) {
        return current.getAndSet(registration);
    }

    public RelyingPartyRegistration current() {
        return current.get();
    }
}
