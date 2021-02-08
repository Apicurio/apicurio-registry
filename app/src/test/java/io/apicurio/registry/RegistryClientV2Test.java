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

package io.apicurio.registry;

import io.apicurio.registry.rest.v2.beans.ArtifactMetaData;
import io.apicurio.registry.rest.v2.beans.ArtifactSearchResults;
import io.apicurio.registry.rest.v2.beans.IfExists;
import io.apicurio.registry.rest.v2.beans.SortBy;
import io.apicurio.registry.rest.v2.beans.SortOrder;
import io.apicurio.registry.types.ArtifactType;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Carles Arnal <carnalca@redhat.com>
 */
@QuarkusTest
public class RegistryClientV2Test extends AbstractResourceTestBase {

	private static final String TEST_GROUP_ID = "TEST_GROUP_ID";

	@Test
	public void testSmoke() throws Exception {

		final String artifactId1 = generateArtifactId();
		final String artifactId2 = generateArtifactId();

		createArtifact(artifactId1);
		createArtifact(artifactId2);

		final ArtifactSearchResults searchResults = clientV2.listArtifactsInGroup(TEST_GROUP_ID, 2, 0, SortOrder.asc, SortBy.name);

		assertNotNull(clientV2.toString());
		assertEquals(clientV2.hashCode(), clientV2.hashCode());
		assertEquals(2, searchResults.getCount());

		clientV2.deleteArtifact(TEST_GROUP_ID, artifactId1);
		clientV2.deleteArtifact(TEST_GROUP_ID, artifactId2);

		final ArtifactSearchResults deletedResults = clientV2.listArtifactsInGroup(TEST_GROUP_ID, 2, 0, SortOrder.asc, SortBy.name);
		assertEquals(0, deletedResults.getCount());
	}


	@Test
	public void getLatestArtifact() {

		String artifactId = generateArtifactId();

		createArtifact(artifactId);

		InputStream amd = clientV2.getLatestArtifact(TEST_GROUP_ID, artifactId);

		assertNotNull(amd);
	}


	private String createArtifact(String artifactId) {
		ByteArrayInputStream stream = new ByteArrayInputStream("{\"name\":\"redhat\"}".getBytes(StandardCharsets.UTF_8));

		ArtifactMetaData created = clientV2.createArtifact(TEST_GROUP_ID, ArtifactType.JSON, artifactId, "1", IfExists.RETURN, false, stream);

		assertNotNull(created);
		assertEquals(TEST_GROUP_ID, created.getGroupId());
		assertEquals(artifactId, created.getId());

		ArtifactMetaData amd = clientV2.getArtifactMetaData(TEST_GROUP_ID, artifactId);

		return amd.getId();
	}
}
