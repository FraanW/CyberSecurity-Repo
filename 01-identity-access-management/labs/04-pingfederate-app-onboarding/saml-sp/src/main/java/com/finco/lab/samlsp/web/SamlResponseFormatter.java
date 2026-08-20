package com.finco.lab.samlsp.web;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns the raw SAML Response XML into something a human can read at 11pm during an incident.
 *
 * <p>Two jobs: pretty-print the whole document, and pull out the five or six fields that cause
 * almost every "SSO is broken" ticket — issuer mismatch, audience mismatch, expired conditions,
 * wrong NameID format.</p>
 */
final class SamlResponseFormatter {

    private static final String SAML_NS = "urn:oasis:names:tc:SAML:2.0:assertion";
    private static final String PROTOCOL_NS = "urn:oasis:names:tc:SAML:2.0:protocol";

    private SamlResponseFormatter() {
    }

    static String prettyPrint(String xml) {
        try {
            Document document = parse(xml);
            Transformer transformer = secureTransformerFactory().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(writer));
            return writer.toString();
        } catch (Exception ex) {
            // If it will not parse, the raw text is still more useful than an error page.
            return xml;
        }
    }

    static Map<String, Object> highlights(String xml) {
        Map<String, Object> out = new LinkedHashMap<>();
        try {
            Document document = parse(xml);

            out.put("responseId", attributeOf(document, PROTOCOL_NS, "Response", "ID"));
            out.put("inResponseTo", attributeOf(document, PROTOCOL_NS, "Response", "InResponseTo"));
            out.put("destination", attributeOf(document, PROTOCOL_NS, "Response", "Destination"));
            out.put("statusCode", attributeOf(document, PROTOCOL_NS, "StatusCode", "Value"));
            out.put("issuer", textOf(document, SAML_NS, "Issuer"));
            out.put("audience", textOf(document, SAML_NS, "Audience"));
            out.put("nameId", textOf(document, SAML_NS, "NameID"));
            out.put("nameIdFormat", attributeOf(document, SAML_NS, "NameID", "Format"));
            out.put("conditionsNotBefore", attributeOf(document, SAML_NS, "Conditions", "NotBefore"));
            out.put("conditionsNotOnOrAfter", attributeOf(document, SAML_NS, "Conditions", "NotOnOrAfter"));
            out.put("authnInstant", attributeOf(document, SAML_NS, "AuthnStatement", "AuthnInstant"));
            out.put("sessionIndex", attributeOf(document, SAML_NS, "AuthnStatement", "SessionIndex"));
            out.put("authnContextClassRef", textOf(document, SAML_NS, "AuthnContextClassRef"));
            out.put("signatures", countOf(document, "http://www.w3.org/2000/09/xmldsig#", "Signature"));
            out.put("encryptedAssertions", countOf(document, SAML_NS, "EncryptedAssertion"));
            out.put("attributeNames", attributeNames(document));
        } catch (Exception ex) {
            out.put("error", "Could not parse the SAML response: " + ex.getMessage());
        }
        return out;
    }

    private static Document parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        // The response is already signature-verified by Spring Security at this point, but we
        // still parse it with external entities off — never hand untrusted XML a network stack.
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private static TransformerFactory secureTransformerFactory() throws Exception {
        TransformerFactory factory = TransformerFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        return factory;
    }

    private static String textOf(Document document, String namespace, String localName) {
        NodeList nodes = document.getElementsByTagNameNS(namespace, localName);
        return nodes.getLength() == 0 ? null : nodes.item(0).getTextContent().trim();
    }

    private static String attributeOf(Document document, String namespace, String localName, String attribute) {
        NodeList nodes = document.getElementsByTagNameNS(namespace, localName);
        if (nodes.getLength() == 0) {
            return null;
        }
        String value = ((Element) nodes.item(0)).getAttribute(attribute);
        return value.isEmpty() ? null : value;
    }

    private static int countOf(Document document, String namespace, String localName) {
        return document.getElementsByTagNameNS(namespace, localName).getLength();
    }

    private static List<String> attributeNames(Document document) {
        NodeList nodes = document.getElementsByTagNameNS(SAML_NS, "Attribute");
        List<String> names = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            names.add(((Element) nodes.item(i)).getAttribute("Name"));
        }
        return names;
    }
}
