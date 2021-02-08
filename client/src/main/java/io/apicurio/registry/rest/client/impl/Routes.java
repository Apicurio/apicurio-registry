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

package io.apicurio.registry.rest.client.impl;

/**
 * @author Carles Arnal <carnalca@redhat.com>
 */
public class Routes {

	protected static final String GROUP_BASE_PATH = "groups/%s/artifacts";
	protected static final String ARTIFACTS_BASE_PATH = GROUP_BASE_PATH + "/%s";

	protected static final String ARTIFACT_RULES = ARTIFACTS_BASE_PATH + "/rules";
	protected static final String ARTIFACT_VERSIONS = ARTIFACTS_BASE_PATH + "/versions";
	protected static final String ARTIFACT_VERSION = ARTIFACT_VERSIONS + "/%s";

	protected static final String ARTIFACT_METADATA = ARTIFACTS_BASE_PATH + "/meta";
	protected static final String ARTIFACT_RULE = ARTIFACT_RULES + "/%s";
	protected static final String ARTIFACT_STATE = ARTIFACTS_BASE_PATH + "/state";
	protected static final String ARTIFACT_TEST = ARTIFACTS_BASE_PATH + "/test";

	protected static final String VERSION_METADATA = ARTIFACT_VERSION + "/meta";
	protected static final String VERSION_TEST = ARTIFACT_VERSION + "/state";
}
