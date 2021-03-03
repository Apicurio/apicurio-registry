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
package io.apicurio.tests.multitenancy;

/**
 * @author Fabian Martinez
 */
public class TenantAuthInfo {

    private String realmAuthServerUrl;

    private String realm;

    private String clientId;
    private String clientSecret;

    private String tenantAppUrl;

    public String getRealm() {
        return realm;
    }

    public void setRealm(String realm) {
        this.realm = realm;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getRealmAuthServerUrl() {
        return realmAuthServerUrl;
    }

    public void setRealmAuthServerUrl(String realmAuthServerUrl) {
        this.realmAuthServerUrl = realmAuthServerUrl;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getTenantAppUrl() {
        return tenantAppUrl;
    }

    public void setTenantAppUrl(String tenantAppUrl) {
        this.tenantAppUrl = tenantAppUrl;
    }

}
