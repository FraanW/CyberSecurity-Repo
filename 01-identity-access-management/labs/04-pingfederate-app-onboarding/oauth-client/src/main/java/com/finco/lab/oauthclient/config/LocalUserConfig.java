package com.finco.lab.oauthclient.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * A plain username-and-password login that works with no IdP at all.
 *
 * <p><b>Why this exists.</b> Federation is the second thing an app learns to do. The first is
 * having its own login. Keeping the local login here means the app is <i>live and usable</i> the
 * moment it deploys — and it gives you the before/after comparison that makes SSO click: same
 * app, same session cookie, two completely different ways of proving who you are.</p>
 *
 * <p><b>Not a production pattern.</b> One user, in memory, gone on restart. Real apps behind
 * PingFederate delete their local login precisely so there is only one place a password can
 * leak from.</p>
 */
@Configuration
public class LocalUserConfig {

    private static final Logger log = LoggerFactory.getLogger(LocalUserConfig.class);

    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCrypt: slow by design, so a stolen hash is expensive to crack.
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(
            PasswordEncoder passwordEncoder,
            @Value("${lab.local.username:farhaan}") String username,
            @Value("${lab.local.password:}") String password,
            @Value("${lab.local.roles:USER,LAB}") String roles) {

        String effectivePassword = password;
        if (effectivePassword == null || effectivePassword.isBlank()) {
            effectivePassword = randomPassword();
            log.warn("""

                    ==========================================================================
                     No LAB_LOCAL_PASSWORD was set, so one was generated for this boot:

                         username: {}
                         password: {}

                     It changes on every restart. Set LAB_LOCAL_PASSWORD to pin it.
                    ==========================================================================
                    """, username, effectivePassword);
        }

        UserDetails user = User.withUsername(username)
                .password(passwordEncoder.encode(effectivePassword))
                .roles(roles.split("\\s*,\\s*"))
                .build();
        return new InMemoryUserDetailsManager(user);
    }

    /** 18 random bytes is well past guessing range, and short enough to copy from a log line. */
    private static String randomPassword() {
        byte[] bytes = new byte[18];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
