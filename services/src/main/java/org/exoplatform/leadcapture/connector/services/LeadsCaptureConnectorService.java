/*
 * Copyright (C) 2003-2023 eXo Platform SAS.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see<http://www.gnu.org/licenses/>.
 */

package org.exoplatform.leadcapture.connector.services;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import javax.ws.rs.core.MediaType;

import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.protocol.HTTP;
import org.json.JSONObject;

import org.exoplatform.leadcapture.connector.exception.LeadCaptureConnectionException;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

public class LeadsCaptureConnectorService {

    public static final String LEAD_CAPTURE_CONNECTION_ERROR = "lead.capture.connectionError";
    public static final String LEAD_CAPTURE_SERVER_URL = "leadCapture.server.url";
    public static final String LEAD_CAPTURE_TOKEN = "leadCapture.token";
    public static final String LEAD_CAPTURE_REST_API = "/portal/rest/leadcapture/leadsmanagement/leads";
    public static final String TOKEN = "token";
    public static String serverUrl;
    public static String token;
    private final Log LOG = ExoLogger.getLogger(LeadsCaptureConnectorService.class);
    private HttpClient client;

    public LeadsCaptureConnectorService() {
        serverUrl = System.getProperty(LEAD_CAPTURE_SERVER_URL);
        token = System.getProperty(LEAD_CAPTURE_TOKEN);
    }

    public void
    sendLead(JSONObject lead) throws Exception {
        if (StringUtils.isAllEmpty(serverUrl)) {
            throw new IllegalStateException("Lead capture server url is not defined");
        }
        if (StringUtils.isAllEmpty(token)) {
            throw new IllegalStateException("Lead capture token is not defined");
        }
        String url = serverUrl + LEAD_CAPTURE_REST_API;
        URI uri = URI.create(url);
        try {
            JSONObject leadInfo = new JSONObject();
            leadInfo.put("lead", lead);
            String response = processPost(uri, leadInfo.toString(), token);
            if (response != null) {
                LOG.info("Lead {} has been sent to the Lead capture server", lead.get("mail"));
            } else {
                throw new IllegalAccessException("leadCapture.unauthorizedOperation");
            }
        } catch (LeadCaptureConnectionException e) {
            throw new IllegalStateException("Unable to open Lead Capture server connection", e);
        }
    }

    private String processPost(URI uri, String jsonString, String accessToken) throws LeadCaptureConnectionException {
        HttpClient httpClient = getHttpClient();
        HttpPost request = new HttpPost(uri);
        StringEntity entity = new StringEntity(jsonString, ContentType.APPLICATION_JSON);
        try {
            request.setHeader(HTTP.CONTENT_TYPE, MediaType.APPLICATION_JSON);
            request.setHeader(TOKEN, accessToken);
            request.setEntity(entity);
            return processRequest(httpClient, request);
        } catch (IOException e) {
            throw new LeadCaptureConnectionException(LEAD_CAPTURE_CONNECTION_ERROR, e);
        }
    }

    private String processRequest(HttpClient httpClient, HttpRequestBase request) throws IOException,
            LeadCaptureConnectionException {
        HttpResponse response = httpClient.execute(request);
        boolean isSuccess = response != null
                && (response.getStatusLine().getStatusCode() >= 200 && response.getStatusLine().getStatusCode() < 300);
        if (isSuccess) {
            return processSuccessResponse(response);
        } else if (response != null && response.getStatusLine().getStatusCode() == 404) {
            return null;
        } else {
            processErrorResponse(response);
            return null;
        }
    }

    private HttpClient getHttpClient() {
        if (client == null) {
            HttpClientConnectionManager clientConnectionManager = getClientConnectionManager();
            HttpClientBuilder httpClientBuilder = HttpClients.custom()
                    .setConnectionManager(clientConnectionManager)
                    .setConnectionReuseStrategy(new DefaultConnectionReuseStrategy());
            client = httpClientBuilder.build();
        }
        return client;
    }

    private HttpClientConnectionManager getClientConnectionManager() {
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setDefaultMaxPerRoute(10);
        return connectionManager;
    }

    private String processSuccessResponse(HttpResponse response) throws IOException {
        if (response.getStatusLine().getStatusCode() == HttpStatus.SC_NO_CONTENT) {
            return String.valueOf(HttpStatus.SC_NO_CONTENT);
        } else if ((response.getStatusLine().getStatusCode() == HttpStatus.SC_CREATED
                || response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) && response.getEntity() != null
                && response.getEntity().getContentLength() != 0) {
            try (InputStream is = response.getEntity().getContent()) {
                return IOUtils.toString(is, StandardCharsets.UTF_8);
            }
        } else {
            return null;
        }
    }

    private void processErrorResponse(HttpResponse response) throws LeadCaptureConnectionException, IOException {
        if (response == null) {
            throw new LeadCaptureConnectionException("Error when connecting to the lead capture server");
        } else if (response.getEntity() != null) {
            try (InputStream is = response.getEntity().getContent()) {
                String errorMessage = IOUtils.toString(is, StandardCharsets.UTF_8);
                if (StringUtils.contains(errorMessage, "")) {
                    throw new LeadCaptureConnectionException(errorMessage);
                } else {
                    throw new LeadCaptureConnectionException(LEAD_CAPTURE_CONNECTION_ERROR + errorMessage);
                }
            }
        } else {
            throw new LeadCaptureConnectionException(LEAD_CAPTURE_CONNECTION_ERROR + response.getStatusLine().getStatusCode());
        }
    }

}
