/*
 * Copyright 2020 Red Hat
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

package io.apicurio.registry.client.service;

import io.apicurio.registry.rest.beans.Rule;
import io.apicurio.registry.types.RuleType;
import retrofit2.Call;
import retrofit2.http.*;

import java.util.List;

/**
 * @author Carles Arnal <carnalca@redhat.com>
 */
public interface RulesService {

    @GET("rules/{rule}")
    Call<Rule> getGlobalRuleConfig(@Path("rule") RuleType rule);

    @PUT("rules/{rule}")
    Call<Rule> updateGlobalRuleConfig(@Path("rule") RuleType rule, @Body Rule data);

    @DELETE("rules/{rule}")
    Call<Void> deleteGlobalRule(@Path("rule") RuleType rule);

    @GET("rules")
    Call<List<RuleType>> listGlobalRules();

    @POST("rules")
    Call<Void> createGlobalRule(@Body Rule data);

    @DELETE("rules")
    Call<Void> deleteAllGlobalRules();
}
