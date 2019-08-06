/*
 * Copyright 2019 Red Hat
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

package io.apicurio.registry.rest;

import java.util.List;

import javax.ws.rs.core.Request;

import io.apicurio.registry.rest.beans.ArtifactMetaData;
import io.apicurio.registry.rest.beans.EditableMetaData;
import io.apicurio.registry.rest.beans.Rule;
import io.apicurio.registry.rest.beans.VersionMetaData;

/**
 * @author eric.wittmann@gmail.com
 */
public class ArtifactsResourceImpl implements ArtifactsResource {

    /**
     * @see io.apicurio.registry.rest.ArtifactsResource#createArtifact(java.lang.String, javax.ws.rs.core.Request)
     */
    @Override
    public ArtifactMetaData createArtifact(String X_Registry_ArtifactType, Request data) {
        // TODO Auto-generated method stub
        return null;
    }

    /**
     * @see io.apicurio.registry.rest.ArtifactsResource#getLatestArtifact(java.lang.String)
     */
    @Override
    public void getLatestArtifact(String artifactId) {
        // TODO Auto-generated method stub
        
    }

    /**
     * @see io.apicurio.registry.rest.ArtifactsResource#updateArtifact(java.lang.String, javax.ws.rs.core.Request)
     */
    @Override
    public ArtifactMetaData updateArtifact(String artifactId, Request data) {
        // TODO Auto-generated method stub
        return null;
    }

    /**
     * @see io.apicurio.registry.rest.ArtifactsResource#deleteArtifact(java.lang.String)
     */
    @Override
    public void deleteArtifact(String artifactId) {
        // TODO Auto-generated method stub
        
    }

    /**
     * @see io.apicurio.registry.rest.ArtifactsResource#listArtifactVersions(java.lang.String)
     */
    @Override
    public List<Integer> listArtifactVersions(String artifactId) {
        // TODO Auto-generated method stub
        return null;
    }

    /**
     * @see io.apicurio.registry.rest.ArtifactsResource#createArtifactVersion(java.lang.String, javax.ws.rs.core.Request)
     */
    @Override
    public VersionMetaData createArtifactVersion(String artifactId, Request data) {
        // TODO Auto-generated method stub
        return null;
    }

    /**
     * @see io.apicurio.registry.rest.ArtifactsResource#getArtifactVersion(java.lang.Integer, java.lang.String)
     */
    @Override
    public void getArtifactVersion(Integer version, String artifactId) {
        // TODO Auto-generated method stub
        
    }

    /**
     * @see io.apicurio.registry.rest.ArtifactsResource#deleteArtifactVersion(java.lang.Integer, java.lang.String)
     */
    @Override
    public void deleteArtifactVersion(Integer version, String artifactId) {
        // TODO Auto-generated method stub
        
    }

    /**
     * @see io.apicurio.registry.rest.ArtifactsResource#listArtifactRules(java.lang.String)
     */
    @Override
    public List<Rule> listArtifactRules(String artifactId) {
        // TODO Auto-generated method stub
        return null;
    }

    /**
     * @see io.apicurio.registry.rest.ArtifactsResource#createArtifactRule(java.lang.String, io.apicurio.registry.rest.beans.Rule)
     */
    @Override
    public void createArtifactRule(String artifactId, Rule data) {
        // TODO Auto-generated method stub
        
    }

    /**
     * @see io.apicurio.registry.rest.ArtifactsResource#deleteArtifactRules(java.lang.String)
     */
    @Override
    public void deleteArtifactRules(String artifactId) {
        // TODO Auto-generated method stub
        
    }

    /**
     * @see io.apicurio.registry.rest.ArtifactsResource#getArtifactRuleConfig(java.lang.String, java.lang.String)
     */
    @Override
    public Rule getArtifactRuleConfig(String rule, String artifactId) {
        // TODO Auto-generated method stub
        return null;
    }

    /**
     * @see io.apicurio.registry.rest.ArtifactsResource#updateArtifactRuleConfig(java.lang.String, java.lang.String, io.apicurio.registry.rest.beans.Rule)
     */
    @Override
    public Rule updateArtifactRuleConfig(String rule, String artifactId, Rule data) {
        // TODO Auto-generated method stub
        return null;
    }

    /**
     * @see io.apicurio.registry.rest.ArtifactsResource#deleteArtifactRule(java.lang.String, java.lang.String)
     */
    @Override
    public void deleteArtifactRule(String rule, String artifactId) {
        // TODO Auto-generated method stub
        
    }

    /**
     * @see io.apicurio.registry.rest.ArtifactsResource#getArtifactMetaData(java.lang.String)
     */
    @Override
    public ArtifactMetaData getArtifactMetaData(String artifactId) {
        // TODO Auto-generated method stub
        return null;
    }

    /**
     * @see io.apicurio.registry.rest.ArtifactsResource#updateArtifactMetaData(java.lang.String, io.apicurio.registry.rest.beans.EditableMetaData)
     */
    @Override
    public void updateArtifactMetaData(String artifactId, EditableMetaData data) {
        // TODO Auto-generated method stub
        
    }

    /**
     * @see io.apicurio.registry.rest.ArtifactsResource#getArtifactVersionMetaData(java.lang.Integer, java.lang.String)
     */
    @Override
    public VersionMetaData getArtifactVersionMetaData(Integer version, String artifactId) {
        // TODO Auto-generated method stub
        return null;
    }

    /**
     * @see io.apicurio.registry.rest.ArtifactsResource#updateArtifactVersionMetaData(java.lang.Integer, java.lang.String, io.apicurio.registry.rest.beans.EditableMetaData)
     */
    @Override
    public void updateArtifactVersionMetaData(Integer version, String artifactId, EditableMetaData data) {
        // TODO Auto-generated method stub
        
    }

    /**
     * @see io.apicurio.registry.rest.ArtifactsResource#deleteArtifactVersionMetaData(java.lang.Integer, java.lang.String)
     */
    @Override
    public void deleteArtifactVersionMetaData(Integer version, String artifactId) {
        // TODO Auto-generated method stub
        
    }

}
