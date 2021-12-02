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

package io.apicurio.tests.utils.subUtils;

import io.apicurio.registry.types.RuleType;
import io.apicurio.tests.common.utils.BaseHttpUtils;
import io.apicurio.tests.common.utils.subUtils.RestConstants;
import io.restassured.response.Response;

public class GlobalRuleUtils {

    public static Response createGlobalRule(String rule) {
        return createGlobalRule(rule, 204);
    }

    public static Response createGlobalRule(String rule, int returnCode) {
        return BaseHttpUtils.rulesPostRequest(RestConstants.JSON, rule, "/rules", returnCode);
    }

    public static Response getGlobalRule(RuleType ruleType) {
        return getGlobalRule(ruleType, 200);
    }

    public static Response getGlobalRule(RuleType ruleType, int returnCode) {
        return BaseHttpUtils.rulesGetRequest(RestConstants.JSON, "/rules/" + ruleType, returnCode);
    }

    public static Response updateGlobalRule(RuleType ruleType, String rule) {
        return updateGlobalRule(ruleType, rule, 200);
    }

    public static Response updateGlobalRule(RuleType ruleType, String rule, int returnCode) {
        return BaseHttpUtils.rulesPutRequest(RestConstants.JSON, rule,  "/rules/" + ruleType, returnCode);
    }

    public static Response deleteGlobalRule(RuleType ruleType) {
        return deleteGlobalRule(ruleType, 204);
    }

    public static Response deleteGlobalRule(RuleType ruleType, int returnCode) {
        return BaseHttpUtils.rulesDeleteRequest(RestConstants.JSON, "/rules/" + ruleType, returnCode);
    }

    public static Response listGlobalRules() {
        return BaseHttpUtils.getRequest(RestConstants.JSON, "/rules", 200);
    }

    public static Response deleteAllGlobalRules() {
        return BaseHttpUtils.deleteRequest(RestConstants.JSON, "/rules", 204);
    }

    public static Response deleteAllGlobalRules(String artifactId) {
        return BaseHttpUtils.deleteRequest(RestConstants.JSON, "/artifacts/" + artifactId + "/rules", 204);
    }

}
