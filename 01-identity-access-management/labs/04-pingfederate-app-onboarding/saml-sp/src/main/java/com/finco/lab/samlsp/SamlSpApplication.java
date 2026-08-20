package com.finco.lab.samlsp;

import com.finco.lab.samlsp.config.SamlProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * A deliberately small SAML 2.0 Service Provider ("the app that trusts the login").
 *
 * <p>Its whole job is to be onboarded into PingFederate as an SP connection, and then to
 * show you — on screen — every field that came back in the assertion.</p>
 */
@SpringBootApplication
@EnableConfigurationProperties(SamlProperties.class)
public class SamlSpApplication {

    public static void main(String[] args) {
        SpringApplication.run(SamlSpApplication.class, args);
    }
}
