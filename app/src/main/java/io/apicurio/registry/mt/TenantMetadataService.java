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

package io.apicurio.registry.mt;

import io.apicurio.tenantmanager.api.datamodel.ApicurioTenant;
import io.apicurio.tenantmanager.api.datamodel.TenantStatusValue;
import io.apicurio.tenantmanager.api.datamodel.UpdateApicurioTenantRequest;
import io.apicurio.tenantmanager.client.TenantManagerClient;
import io.apicurio.rest.client.auth.exception.ForbiddenException;
import io.apicurio.rest.client.auth.exception.NotAuthorizedException;
import io.apicurio.tenantmanager.client.exception.ApicurioTenantNotFoundException;
import io.apicurio.registry.utils.OptionalBean;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import static io.apicurio.registry.faulttolerance.FaultToleranceConstants.TIMEOUT_MS;

/**
 * @author Fabian Martinez
 * @author Jakub Senko <jsenko@redhat.com>
 */
@ApplicationScoped
public class TenantMetadataService {

    @Inject
    OptionalBean<TenantManagerClient> tenantManagerClient;

    @Retry(abortOn = {
            UnsupportedOperationException.class, TenantNotFoundException.class,
            TenantNotAuthorizedException.class, TenantForbiddenException.class
    }) // 3 retries, 200ms jitter
    @Timeout(TIMEOUT_MS)
    public ApicurioTenant getTenant(String tenantId) throws TenantNotFoundException {
        if (tenantManagerClient.isEmpty()) {
            throw new UnsupportedOperationException("Multitenancy is not enabled");
        }
        try {
            return tenantManagerClient.get().getTenant(tenantId);
        } catch (ApicurioTenantNotFoundException e) {
            throw new TenantNotFoundException(e.getMessage());
        } catch (NotAuthorizedException e) {
            throw new TenantNotAuthorizedException(e.getMessage());
        } catch (ForbiddenException e) {
            throw new TenantForbiddenException(e.getMessage());
        }
    }

    @Retry(abortOn = {
            UnsupportedOperationException.class, TenantNotFoundException.class,
            TenantNotAuthorizedException.class, TenantForbiddenException.class
    }) // 3 retries, 200ms jitter
    @Timeout(TIMEOUT_MS)
    public void markTenantAsDeleted(String tenantId) {
        if (tenantManagerClient.isEmpty()) {
            throw new UnsupportedOperationException("Multitenancy is not enabled");
        }
        try {
            UpdateApicurioTenantRequest ureq = new UpdateApicurioTenantRequest();
            ureq.setStatus(TenantStatusValue.DELETED);
            tenantManagerClient.get().updateTenant(tenantId, ureq);
        } catch (ApicurioTenantNotFoundException e) {
            throw new TenantNotFoundException(e.getMessage());
        } catch (NotAuthorizedException e) {
            throw new TenantNotAuthorizedException(e.getMessage());
        } catch (ForbiddenException e) {
            throw new TenantForbiddenException(e.getMessage());
        }
    }
}
