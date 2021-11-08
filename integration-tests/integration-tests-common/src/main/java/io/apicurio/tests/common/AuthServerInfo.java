/*
 * Copyright 2021 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.tests.common;

/**
 * @author Fabian Martinez
 */
public class AuthServerInfo {

    String authServerUrl;
    String realm;

    String adminClientId;
    String adminClientSecret;

    String developerClientId;
    String developerClientSecret;

    String readOnlyClientId;
    String readOnlyClientSecret;
    /**
     * @return the authServerUrl
     */
    public String getAuthServerUrl() {
        return authServerUrl;
    }

    /**
     * @return the authServerUrl configured with the realm
     */
    public String getAuthServerUrlConfigured() {
        return authServerUrl + "/realms/" + realm + "/protocol/openid-connect/token";
    }
    /**
     * @param authServerUrl the authServerUrl to set
     */
    public void setAuthServerUrl(String authServerUrl) {
        this.authServerUrl = authServerUrl;
    }
    /**
     * @return the realm
     */
    public String getRealm() {
        return realm;
    }
    /**
     * @param realm the realm to set
     */
    public void setRealm(String realm) {
        this.realm = realm;
    }
    /**
     * @return the adminClientId
     */
    public String getAdminClientId() {
        return adminClientId;
    }
    /**
     * @param adminClientId the adminClientId to set
     */
    public void setAdminClientId(String adminClientId) {
        this.adminClientId = adminClientId;
    }
    /**
     * @return the adminClientSecret
     */
    public String getAdminClientSecret() {
        return adminClientSecret;
    }
    /**
     * @param adminClientSecret the adminClientSecret to set
     */
    public void setAdminClientSecret(String adminClientSecret) {
        this.adminClientSecret = adminClientSecret;
    }
    /**
     * @return the developerClientId
     */
    public String getDeveloperClientId() {
        return developerClientId;
    }
    /**
     * @param developerClientId the developerClientId to set
     */
    public void setDeveloperClientId(String developerClientId) {
        this.developerClientId = developerClientId;
    }
    /**
     * @return the developerClientSecret
     */
    public String getDeveloperClientSecret() {
        return developerClientSecret;
    }
    /**
     * @param developerClientSecret the developerClientSecret to set
     */
    public void setDeveloperClientSecret(String developerClientSecret) {
        this.developerClientSecret = developerClientSecret;
    }
    /**
     * @return the readOnlyClientId
     */
    public String getReadOnlyClientId() {
        return readOnlyClientId;
    }
    /**
     * @param readOnlyClientId the readOnlyClientId to set
     */
    public void setReadOnlyClientId(String readOnlyClientId) {
        this.readOnlyClientId = readOnlyClientId;
    }
    /**
     * @return the readOnlyClientSecret
     */
    public String getReadOnlyClientSecret() {
        return readOnlyClientSecret;
    }
    /**
     * @param readOnlyClientSecret the readOnlyClientSecret to set
     */
    public void setReadOnlyClientSecret(String readOnlyClientSecret) {
        this.readOnlyClientSecret = readOnlyClientSecret;
    }

}
