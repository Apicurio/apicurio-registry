/*
 * Copyright 2022 Red Hat
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

package io.apicurio.registry.noprofile.maven;

import io.apicurio.registry.maven.RegisterRegistryMojo;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * @author Ales Justin
 */
@QuarkusTest
public class RegisterRegistryMojoTest extends RegistryMojoTestBase {
    RegisterRegistryMojo mojo;

    @BeforeEach
    public void createMojo() {
        this.mojo = new RegisterRegistryMojo();
        this.mojo.setRegistryUrl(TestUtils.getRegistryV2ApiUrl(testPort));
    }

    @Test
    public void testRegister() throws IOException, MojoFailureException, MojoExecutionException {
        super.testRegister(mojo, "RegisterRegistryMojoTest");
    }
}
