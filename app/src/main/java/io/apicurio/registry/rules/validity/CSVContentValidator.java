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

package io.apicurio.registry.rules.validity;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import javax.enterprise.context.ApplicationScoped;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.rules.RuleViolationException;
import io.apicurio.registry.types.RuleType;
import uk.gov.nationalarchives.csv.validator.api.java.CsvValidator;
import uk.gov.nationalarchives.csv.validator.api.java.FailMessage;
import uk.gov.nationalarchives.csv.validator.api.java.Substitution;

/**
 * A content validator implementation for the CSV content type.
 * @author jeffstov@gmail.com
 */
@ApplicationScoped
public class CSVContentValidator implements ContentValidator {
    
    /**
     * Constructor.
     */
    public CSVContentValidator() {
    }
    
    protected final static Logger log = LoggerFactory.getLogger(CSVContentValidator.class);
    
    /**
     * @see io.apicurio.registry.rules.validity.ContentValidator#validate(io.apicurio.registry.rules.validity.ValidityLevel, ContentHandle)
     */
    @Override
    public void validate(ValidityLevel level, ContentHandle content) throws RuleViolationException {
       log.info("In CSV content validator.");
	   List<FailMessage> failures = CsvValidator.validate(new InputStreamReader(new ByteArrayInputStream("test,test,test".getBytes())), new InputStreamReader(content.stream()), false,  new ArrayList<Substitution>(), Boolean.TRUE, Boolean.FALSE);
       if(failures != null && !failures.isEmpty()) {
       	for(FailMessage msg : failures) {
       		
       		if(failures.size() == 1 && msg.getMessage().startsWith("Metadata header, cannot find")) {
       			log.info("No errors in schema.");
       			return;
       		} else {
       			log.info("Error in CSV Schema: " + msg.getMessage());
       			throw new RuleViolationException("Syntax violation for CSV artifact.", RuleType.VALIDITY, level.name(), new Exception(msg.getMessage()));
       		}
       		
       	}
       	
       }
       
    	
    }

}
