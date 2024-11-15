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

package apicurio.common.app.components.config.index;

import io.apicurio.common.apps.config.DynamicConfigPropertyDef;
import io.apicurio.common.apps.config.DynamicConfigPropertyList;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;

import java.util.List;

@Recorder
public class DynamicPropertiesInfoRecorder {

    public RuntimeValue<DynamicConfigPropertyList> initializePropertiesInfo(
            List<DynamicConfigPropertyDef> dynamicProperties) {

        return new RuntimeValue<>(new DynamicConfigPropertyList(dynamicProperties));
    }
}
