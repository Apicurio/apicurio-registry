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
package io.apicurio.tests.common.interfaces;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Collections;

@ExtendWith(ExtensionContextParameterResolver.class)
public interface TestSeparator {
    Logger SEPARATOR_LOGGER = LoggerFactory.getLogger(TestSeparator.class);
    String SEPARATOR_CHAR = "#";

    @BeforeEach
    default void beforeEachTest(TestInfo testInfo) {
        SEPARATOR_LOGGER.info(String.join("", Collections.nCopies(76, SEPARATOR_CHAR)));
        SEPARATOR_LOGGER.info(String.format("%s.%s-STARTED", testInfo.getTestClass().get().getName(), testInfo.getTestMethod().get().getName()));
    }

    @AfterEach
    default void afterEachTest(TestInfo testInfo) {
        SEPARATOR_LOGGER.info(String.format("%s.%s-FINISHED", testInfo.getTestClass().get().getName(), testInfo.getTestMethod().get().getName()));
        SEPARATOR_LOGGER.info(String.join("", Collections.nCopies(76, SEPARATOR_CHAR)));
    }
}