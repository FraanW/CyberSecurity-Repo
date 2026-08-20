package com.finco.lab.oauthclient;

import com.finco.lab.oauthclient.config.PingFedProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * A small OAuth 2.0 / OpenID Connect <b>client</b> ("relying party") built to be onboarded into
 * PingFederate as an OAuth client, then to show you every token it receives, decoded.
 */
@SpringBootApplication
@EnableConfigurationProperties(PingFedProperties.class)
public class OauthClientApplication {

    public static void main(String[] args) {
        SpringApplication.run(OauthClientApplication.class, args);
    }
}
