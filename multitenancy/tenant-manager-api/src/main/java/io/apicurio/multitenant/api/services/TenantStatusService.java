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

package io.apicurio.multitenant.api.services;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.apicurio.multitenant.api.datamodel.TenantStatusValue;
import io.apicurio.multitenant.storage.dto.RegistryTenantDto;
import io.apicurio.multitenant.utils.Pair;

/**
 * @author Fabian Martinez
 */
@ApplicationScoped
public class TenantStatusService {

    private List<Pair<TenantStatusValue, TenantStatusValue>> allowedTransitions;

    @ConfigProperty(name = "tenant-manager.status-transitions.additional-allowed-test-transition", defaultValue = "false")
    Boolean additionalAllowedTestTransition;

    @PostConstruct
    public void init() {
        List<Pair<TenantStatusValue, TenantStatusValue>> transitions = new ArrayList<>(List.of(
                    Pair.of(TenantStatusValue.READY, TenantStatusValue.TO_BE_DELETED),
                    Pair.of(TenantStatusValue.TO_BE_DELETED, TenantStatusValue.DELETED)
                ));
        if (additionalAllowedTestTransition) {
            transitions.add(Pair.of(TenantStatusValue.DELETED, TenantStatusValue.READY));
        }
        allowedTransitions = Collections.unmodifiableList(transitions);
    }

    public boolean verifyTenantStatusChange(RegistryTenantDto tenant, TenantStatusValue newStatus) {
        TenantStatusValue fromStatus = TenantStatusValue.fromValue(tenant.getStatus());
        if (fromStatus == newStatus) {
            return true;
        }
        for (Pair<TenantStatusValue, TenantStatusValue> allowedTransition : allowedTransitions) {
            if (fromStatus == allowedTransition.getLeft() && newStatus == allowedTransition.getRight()) {
                //allowed
                return true;
            }
        }
        //not allowed
        return false;
    }

}
