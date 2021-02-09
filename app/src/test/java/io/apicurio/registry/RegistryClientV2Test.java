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
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Carles Arnal <carnalca@redhat.com>
 */
@QuarkusTest
public class RegistryClientV2Test extends AbstractResourceTestBase {

	private static final String ARTIFACT_CONTENT = "{\"name\":\"redhat\"}";

	@Test
	public void testSmoke() throws Exception {
		final String groupId = "testSmoke";
		final String artifactId1 = generateArtifactId();
		final String artifactId2 = generateArtifactId();

		createArtifact(groupId, artifactId1);
		createArtifact(groupId, artifactId2);

		final ArtifactSearchResults searchResults = clientV2
				.listArtifactsInGroup(groupId, 2, 0, SortOrder.asc, SortBy.name);

		assertNotNull(clientV2.toString());
		assertEquals(clientV2.hashCode(), clientV2.hashCode());
		assertEquals(2, searchResults.getCount());

		clientV2.deleteArtifact(groupId, artifactId1);
		clientV2.deleteArtifact(groupId, artifactId2);

		final ArtifactSearchResults deletedResults = clientV2
				.listArtifactsInGroup(groupId, 2, 0, SortOrder.asc, SortBy.name);
		assertEquals(0, deletedResults.getCount());
	}

	@Test
	void testSearchArtifact() throws Exception {

		final String groupId = "testSearchArtifact";
		// warm-up
		clientV2.listArtifactsInGroup(groupId);

		String artifactId = UUID.randomUUID().toString();
		String name = "n" + ThreadLocalRandom.current().nextInt(1000000);
		ByteArrayInputStream artifactData = new ByteArrayInputStream(
				("{\"type\":\"record\",\"title\":\"" + name
						+ "\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}")
						.getBytes(StandardCharsets.UTF_8));

		ArtifactMetaData amd = clientV2.createArtifact(groupId, ArtifactType.JSON, artifactId, artifactData);
		long id = amd.getGlobalId();

		this.waitForGlobalId(id);

		ArtifactSearchResults results = clientV2
				.searchArtifacts(name, 0, 10, SortOrder.asc, SortBy.name, Collections.emptyList(),
						Collections.emptyList(), "", "");
		Assertions.assertNotNull(results);
		Assertions.assertEquals(1, results.getCount());
		Assertions.assertEquals(1, results.getArtifacts().size());
		Assertions.assertEquals(name, results.getArtifacts().get(0).getName());

		// Try searching for *everything*.  This test was added due to Issue #661
		results = clientV2.searchArtifacts(null, null, null, null, null, null, null, null, null);
		Assertions.assertNotNull(results);
		Assertions.assertTrue(results.getCount() > 0);
	}

	@Test
	public void getLatestArtifact() {
		final String groupId = "getLatestArtifact";
		final String artifactId = generateArtifactId();

		createArtifact(groupId, artifactId);

		InputStream amd = clientV2.getLatestArtifact(groupId, artifactId);

		assertNotNull(amd);
	}


	@Test
	public void getContentById() throws IOException {
		final String groupId = "getContentById";
		final String artifactId = generateArtifactId();

		ArtifactMetaData amd = createArtifact(groupId, artifactId);

		assertNotNull(amd.getContentId());

		InputStream content = clientV2.getContentById(amd.getContentId());
		assertNotNull(content);

		String artifactContent = IOUtils.toString(content, StandardCharsets.UTF_8);
		assertEquals(ARTIFACT_CONTENT, artifactContent);
	}


	@Test
	public void getContentByHash() throws IOException {
		final String groupId = "getContentByHash";
		final String artifactId = generateArtifactId();
		String contentHash = DigestUtils.sha256Hex(ARTIFACT_CONTENT);

		createArtifact(groupId, artifactId);

		InputStream content = clientV2.getContentByHash(contentHash);
		assertNotNull(content);

		String artifactContent = IOUtils.toString(content, StandardCharsets.UTF_8);
		assertEquals(ARTIFACT_CONTENT, artifactContent);
	}


	@Test
	public void getContentByGlobalId() throws IOException {
		final String groupId = "getContentByGlobalId";
		final String artifactId = generateArtifactId();

		ArtifactMetaData amd = createArtifact(groupId, artifactId);

		InputStream content = clientV2.getContentByGlobalId(amd.getGlobalId());
		assertNotNull(content);

		String artifactContent = IOUtils.toString(content, StandardCharsets.UTF_8);
		assertEquals(ARTIFACT_CONTENT, artifactContent);
	}

	private ArtifactMetaData createArtifact(String groupId, String artifactId) {
		ByteArrayInputStream stream = new ByteArrayInputStream(
				ARTIFACT_CONTENT.getBytes(StandardCharsets.UTF_8));

		ArtifactMetaData created = clientV2
				.createArtifact(groupId, ArtifactType.JSON, artifactId, "1", IfExists.RETURN, false, stream);

		assertNotNull(created);
		assertEquals(groupId, created.getGroupId());
		assertEquals(artifactId, created.getId());

		return created;
	}
}
