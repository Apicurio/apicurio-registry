package io.apicurio.common.apps.logging.audit;

import java.util.Map;

/**
 * Classes that implement this method get the opportunity to extract metadata property values from objects
 * during auditing. This allows extraction of relevant auditable data from complex objects in methods
 * annotated with <code>@Audited</code>. Without providing one of these for a complex object, the auditing
 * system will simply call toString() on any method parameter that is being included in the audit log. Note
 * that metadata extractors are only used when no parameters are defined on the <code>@Audited</code>
 * annotation.
 *
 * @author eric.wittmann@gmail.com
 */
public interface AuditMetaDataExtractor {

    /**
     * Returns true if this extractor should be used for the given parameter value.
     * 
     * @param parameterValue
     */
    public boolean accept(Object parameterValue);

    /**
     * Extracts metadata from the given parameter value into the given map of metadata.
     * 
     * @param parameterValue
     * @param metaData
     */
    public void extractMetaDataInto(Object parameterValue, Map<String, String> metaData);

}
