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
package org.exoplatform.leadcapture.connector.listeners;

import org.json.JSONObject;

import org.exoplatform.leadcapture.connector.services.LeadsCaptureConnectorService;
import org.exoplatform.services.listener.Asynchronous;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.services.organization.User;
import org.exoplatform.services.organization.UserEventListener;
import org.exoplatform.services.organization.UserProfile;

@Asynchronous
public class NewUserListener extends UserEventListener {

    public static final String CAPTURE_METHODE = "leadCapture.connector.captureMethode";
    public static final String CAPTURE_TYPE = "leadCapture.connector.captureType";
    public static final String PERSON_SOURCE = "leadCapture.connector.personSource";
    public static final String CAPTURE_SOURCE_INFO = "leadCapture.connector.captureSourceInfo";
    private static final Log LOG = ExoLogger.getLogger(NewUserListener.class);
    public static String captureMethod;
    public static String captureType;
    public static String personSource;
    public static String captureSourceInfo;
    public OrganizationService organizationService;
    public LeadsCaptureConnectorService leadsCaptureConnectorService;

    public NewUserListener(OrganizationService organizationService, LeadsCaptureConnectorService leadsCaptureConnectorService) throws Exception {
        this.organizationService = organizationService;
        this.leadsCaptureConnectorService = leadsCaptureConnectorService;
        captureMethod = System.getProperty(CAPTURE_METHODE);
        captureType = System.getProperty(CAPTURE_TYPE);
        personSource = System.getProperty(PERSON_SOURCE);
        captureSourceInfo = System.getProperty(CAPTURE_SOURCE_INFO);
    }

    @Override
    public void postSave(User user, boolean isNew) throws Exception {
        try {
            JSONObject lead = new JSONObject();
            lead.put("mail", user.getEmail());
            lead.put("firstName", user.getFirstName());
            lead.put("lastName", user.getLastName());
            lead.put("captureMethod", captureMethod);
            lead.put("captureType", captureType);
            lead.put("personSource", personSource);
            lead.put("captureSourceInfo", captureSourceInfo);
            UserProfile profile = organizationService.getUserProfileHandler().findUserProfileByName(user.getUserName());
            if (profile != null) {
                lead.put("language", profile.getAttribute("user.language"));
            }
            leadsCaptureConnectorService.sendLead(user.getUserName(), lead);
        } catch (Exception e) {
            LOG.error("an error occured", e);
        }
    }

    @Override
    public void postDelete(User user) throws Exception {
    }

}
