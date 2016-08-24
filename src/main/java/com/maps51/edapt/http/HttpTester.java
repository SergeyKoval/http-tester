package com.maps51.edapt.http;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.StrBuilder;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import java.io.*;
import java.nio.charset.Charset;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public class HttpTester {
    private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(HttpTester.class);
    private static final String HTTP = "http";
    private static final String HTTPS = "https";
    private static final String TLS_V_1 = "TLSv1";
    private static final String TLS_V_1_1 = "TLSv1.1";
    private static final String TLS_V_1_2 = "TLSv1.2";
    private static final String CONFIG_PROPERTIES_PATH = "config.properties";
    private static final String USERNAME_KEY = "username";
    private static final String PASSCODE_KEY = "passcode";
    private static final String URL_KEY = "url";
    private static final List<String> ALLOWED_CONFIG_KEYS = Arrays.asList(USERNAME_KEY, URL_KEY, PASSCODE_KEY);
    private static final String EQUAL_SIGN = "=";
    private static final String SEMICOLON = ";";
    private static final String WHITESPACE = " ";
    private static final String LEFT_CURLY_BRACKET = "{";
    private static final String RIGHT_CURLY_BRACKET = "}";
    private static final String UTF_8 = "UTF-8";

    private static Registry<ConnectionSocketFactory> prepareSocketFactoryRegistry() {
        RegistryBuilder<ConnectionSocketFactory> registryBuilder = RegistryBuilder.create();
        registryBuilder.register(HTTP, PlainConnectionSocketFactory.getSocketFactory());
        registryBuilder.register(HTTPS, prepareTrustingSocketFactory());
        return registryBuilder.build();
    }

    private static SSLConnectionSocketFactory prepareTrustingSocketFactory() {
        TrustStrategy trustAllStrategy = new TrustStrategy() {
            @Override
            public boolean isTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                return true;
            }
        };
        HostnameVerifier allowAllHostnameVerifier = new HostnameVerifier() {
            @Override
            public boolean verify(String s, SSLSession sslSession) {
                return Boolean.TRUE;
            }
        };

        try {
            SSLContext sslContext = SSLContexts.custom().useProtocol(TLS_V_1_2).loadTrustMaterial(trustAllStrategy).build();
            return new SSLConnectionSocketFactory(sslContext, new String[]{TLS_V_1, TLS_V_1_1, TLS_V_1_2}, null, allowAllHostnameVerifier);
        } catch (NoSuchAlgorithmException | KeyManagementException | KeyStoreException e) {
            LOG.error("Couldn't create SSL context: ", e);
            return null;
        }
    }

    private static String buildConfigurationString(Properties config) {
        StrBuilder sb = new StrBuilder();
        sb.append(LEFT_CURLY_BRACKET);
        for (String key : config.stringPropertyNames()) {
            if (ALLOWED_CONFIG_KEYS.contains(key)) {
                sb.append(key).append(EQUAL_SIGN).append(config.getProperty(key)).append(SEMICOLON).append(WHITESPACE);
            }
        }
        return sb.append(RIGHT_CURLY_BRACKET).build();
    }

    private static void processPasscode(Properties properties) {
        LOG.info("Enter passcode or leave empty to use config's one:");
        String passcode = System.console().readLine();
        if (StringUtils.isNotBlank(passcode)) {
            properties.setProperty(PASSCODE_KEY, passcode);
        }
        LOG.info("Using passcode: {}", properties.getProperty(PASSCODE_KEY));
    }

    private static HttpEntity buildEntity(Properties properties) throws UnsupportedEncodingException {
        List<NameValuePair> parameters = new ArrayList<>();
        parameters.add(new BasicNameValuePair(USERNAME_KEY, properties.getProperty(USERNAME_KEY)));
        parameters.add(new BasicNameValuePair(PASSCODE_KEY, properties.getProperty(PASSCODE_KEY)));
        return new UrlEncodedFormEntity(parameters, Charset.forName(UTF_8));
    }

    private static void logResponse(HttpResponse response) throws IOException {
        LOG.info("Response:");
        LOG.info("\tStatus: {}", response.getStatusLine().toString());

        LOG.info("\tResponse headers:");
        for (Header responseHeader : response.getAllHeaders()) {
            LOG.info("\t\t{}: {}", responseHeader.getName(), responseHeader.getValue());
        }

        LOG.info("\tResponse data:");
        HttpEntity entity = response.getEntity();
        LOG.info("\t\tContent-Type: {}", entity.getContentType());
        LOG.info("\t\tContent-Length: {}", entity.getContentLength());
        LOG.info("\t\tContent: '{}'", EntityUtils.toString(entity));
    }


    public static void main(String[] args) throws IOException {
        Properties properties = new Properties();
        LOG.info("Reading configuration from: {}", CONFIG_PROPERTIES_PATH);
        try (InputStreamReader input = new InputStreamReader(new FileInputStream(CONFIG_PROPERTIES_PATH))) {
            properties.load(input);
        } catch (FileNotFoundException e) {
            LOG.error("Error reading config file: {}", e.toString());
            throw e;
        } catch (IOException e) {
            LOG.error("Error loading properties: {}", e.toString());
            throw e;
        }

        LOG.info("Config successfully loaded");
        LOG.info(buildConfigurationString(properties));
        processPasscode(properties);

        LOG.info("Building HTTP entity.");
        HttpEntity entity = buildEntity(properties);
        LOG.info("Building POST method for URL: {}", properties.getProperty(URL_KEY));
        HttpPost request = new HttpPost(properties.getProperty(URL_KEY));
        request.setEntity(entity);

        LOG.info("Building HTTP client & executing HTTP request.");
        try (CloseableHttpClient client = HttpClientBuilder.create()
                .setConnectionManager(new PoolingHttpClientConnectionManager(prepareSocketFactoryRegistry()))
                .setDefaultRequestConfig(RequestConfig.DEFAULT).build();
             CloseableHttpResponse response = client.execute(request)) {
            LOG.info("HTTP request executed successfully.");
            logResponse(response);
        } catch (Exception e) {
            LOG.error("Error occurred during executing HTTP request:  {}", e.toString());
            throw e;
        }
        LOG.info("Congratulations! You are done.");
    }
}
