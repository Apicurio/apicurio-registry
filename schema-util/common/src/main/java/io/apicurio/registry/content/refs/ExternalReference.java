/*
 * Copyright 2023 Red Hat Inc
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

package io.apicurio.registry.content.refs;

import java.util.Objects;

/**
 * Models a reference from one artifact to another.  This represents the information found in the content
 * of an artifact, and is very type-specific.  For example, a JSON schema reference might look like this:
 * 
 * <pre>
 * {
 *   "$ref" : "types/data-types.json#/$defs/FooType"
 * }
 * </pre>
 * 
 * In this case, the fields of this type will be:
 * 
 * <ul>
 *   <li><em>fullReference</em>: <code>types/data-types.json#/$defs/FooType</code></li>
 *   <li><em>resource</em>: <code>types/data-types.json</code></li>
 *   <li><em>component</em>: <code>#/$defs/FooType</code></li>
 * </ul>
 *
 * For an Avro artifact a reference might look like this:
 * 
 * <pre>
 * {
 *   "name": "exchange",
 *   "type": "com.kubetrade.schema.common.Exchange"
 * }
 * </pre>
 * 
 * In this case, the fields of this type will be:
 * 
 * <ul>
 *   <li><em>fullReference</em>: <code>com.kubetrade.schema.common.Exchange</code></li>
 *   <li><em>resource</em>: <code>com.kubetrade.schema.common.Exchange</code></li>
 *   <li><em>component</em>: <em>null</em></li>
 * </ul>
 * 
 * @author eric.wittmann@gmail.com
 */
public class ExternalReference {

    private String fullReference;
    private String resource;
    private String component;
    
    /**
     * Constructor.
     * @param fullReference
     * @param resource
     * @param component
     */
    public ExternalReference(String fullReference, String resource, String component) {
        this.fullReference = fullReference;
        this.resource = resource;
        this.component = component;
    }
    
    /**
     * Constructor.  This variant is useful if there is no component part of an external reference.  In this
     * case the full reference is also the resource (and the component is null).
     * @param reference
     */
    public ExternalReference(String reference) {
        this(reference, reference, null);
    }

    /**
     * @return the fullReference
     */
    public String getFullReference() {
        return fullReference;
    }

    /**
     * @param fullReference the fullReference to set
     */
    public void setFullReference(String fullReference) {
        this.fullReference = fullReference;
    }

    /**
     * @return the resource
     */
    public String getResource() {
        return resource;
    }

    /**
     * @param resource the resource to set
     */
    public void setResource(String resource) {
        this.resource = resource;
    }

    /**
     * @return the component
     */
    public String getComponent() {
        return component;
    }

    /**
     * @param component the component to set
     */
    public void setComponent(String component) {
        this.component = component;
    }

    /**
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        return Objects.hash(fullReference);
    }

    /**
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ExternalReference other = (ExternalReference) obj;
        return Objects.equals(fullReference, other.fullReference);
    }
    
    /**
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return this.fullReference;
    }

}
