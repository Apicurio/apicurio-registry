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

package apicurio.common.app.components.config.index.deployment;

import apicurio.common.app.components.config.index.DynamicPropertiesInfoRecorder;
import io.apicurio.common.apps.config.Dynamic;
import io.apicurio.common.apps.config.DynamicConfigPropertyDef;
import io.apicurio.common.apps.config.DynamicConfigPropertyList;
import io.quarkus.arc.deployment.BeanDiscoveryFinishedBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.processor.InjectionPointInfo;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.runtime.RuntimeValue;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

class ConfigIndexProcessor {

    private static final Logger log = LoggerFactory.getLogger(ConfigIndexProcessor.class);

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void syntheticBean(DynamicPropertiesInfoRecorder recorder, BeanDiscoveryFinishedBuildItem beanDiscovery,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeans) {
        List<DynamicConfigPropertyDef> dynamicProperties = beanDiscovery.getInjectionPoints().stream()
                .filter(ConfigIndexProcessor::isDynamicConfigProperty).map(injectionPointInfo -> {
                    try {
                        AnnotationInstance configPropertyAI = injectionPointInfo
                                .getRequiredQualifier(DotName.createSimple(ConfigProperty.class.getName()));
                        AnnotationInstance dynamicAI = injectionPointInfo
                                .getRequiredQualifier(DotName.createSimple(Dynamic.class.getName()));

                        Type supplierType = injectionPointInfo.getRequiredType();
                        Type actualType = supplierType.asParameterizedType().arguments().get(0);

                        final String propertyName = configPropertyAI.value("name").asString();
                        final Class<?> propertyType = Class.forName(actualType.name().toString());
                        final AnnotationValue defaultValueAV = configPropertyAI.value("defaultValue");
                        if (defaultValueAV == null) {
                            throw new RuntimeException("Dynamic configuration property '" + propertyName
                                    + "' must have a default value.");
                        }
                        final String defaultValue = defaultValueAV.asString();
                        DynamicConfigPropertyDef def = new DynamicConfigPropertyDef(propertyName,
                                propertyType, defaultValue);

                        final AnnotationValue labelAV = dynamicAI.value("label");
                        final AnnotationValue descriptionAV = dynamicAI.value("description");
                        final AnnotationValue requiresAV = dynamicAI.value("requires");
                        if (labelAV != null) {
                            def.setLabel(labelAV.asString());
                        }
                        if (descriptionAV != null) {
                            def.setDescription(descriptionAV.asString());
                        }
                        if (requiresAV != null) {
                            def.setRequires(requiresAV.asStringArray());
                        }

                        return def;
                    } catch (Exception e) {
                        if (e.getMessage().contains("Not a parameterized type")) {
                            log.error("Invalid type for @Dynamic config property (must be Supplier<Type>)");
                        }
                        throw new RuntimeException(e);
                    }
                }).collect(Collectors.toList());

        final RuntimeValue<DynamicConfigPropertyList> dynamicPropertiesHolderRuntimeValue = recorder
                .initializePropertiesInfo(dynamicProperties);

        syntheticBeans.produce(SyntheticBeanBuildItem.configure(DynamicConfigPropertyList.class)
                .runtimeValue(dynamicPropertiesHolderRuntimeValue).unremovable().setRuntimeInit().done());
    }

    private static boolean isDynamicConfigProperty(InjectionPointInfo injectionPointInfo) {
        return injectionPointInfo
                .getRequiredQualifier(DotName.createSimple(ConfigProperty.class.getName())) != null
                && injectionPointInfo.isField() && injectionPointInfo.getTarget().asField()
                        .annotation(DotName.createSimple(Dynamic.class.getName())) != null;
    }
}
