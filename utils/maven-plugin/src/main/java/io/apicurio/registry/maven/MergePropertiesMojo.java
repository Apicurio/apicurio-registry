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

package io.apicurio.registry.maven;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.util.List;
import java.util.Properties;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * @author eric.wittmann@gmail.com
 */
@Mojo(name = "merge")
public class MergePropertiesMojo extends AbstractMojo {

    @Parameter(required = true)
    File output;
    
    @Parameter(required = true)
    List<File> inputs;
    
    @Parameter(required = false, defaultValue = "false")
    Boolean deleteInputs;

    /**
     * @see org.apache.maven.plugin.Mojo#execute()
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (output == null || !output.getParentFile().isDirectory() || output.isDirectory()) {
            throw new MojoExecutionException("Invalid 'output' file.");
        }
        if (inputs == null || inputs.isEmpty()) {
            throw new MojoExecutionException("Invalid 'inputs'.  Must be a collection of input files.");
        }
        
        Properties mergedProps = new Properties();
        // Read all the input properties files
        getLog().info("Reading " + inputs.size() + " input files.");
        for (File input : inputs) {
            if (!input.isFile() ) {
                throw new MojoExecutionException("Invalid input file: " + input.getAbsolutePath());
            }
            Properties inputProps = new Properties();
            try (Reader reader = new FileReader(input)) {
                inputProps.load(reader);
                mergedProps.putAll(inputProps);
                getLog().info("Read all properties from input file: " + input.getName());
            } catch (Throwable t) {
                throw new MojoExecutionException("Failed to load input file: " + input.getAbsolutePath(), t);
            }
            if (deleteInputs) {
                input.delete();
                getLog().info("Deleted input file: " + input.getName());
            }
        }
        
        // Write out the merged properties to the output file.
        if (output.isFile()) {
            output.delete();
        }
        try (FileWriter writer = new FileWriter(output)) {
            mergedProps.store(writer, "Properties merged by 'apicurio-registry-maven-plugin'");
            getLog().info("Merged properties written to: " + output.getName());
        } catch (Throwable t) {
            throw new MojoExecutionException("Failed to write merged properties to: " + output.getAbsolutePath());
        }
    }

}
