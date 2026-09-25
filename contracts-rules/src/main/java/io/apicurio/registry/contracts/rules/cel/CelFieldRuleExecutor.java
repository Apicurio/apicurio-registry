package io.apicurio.registry.contracts.rules.cel;

import io.apicurio.registry.contracts.rules.ContractRuleContext;
import io.apicurio.registry.contracts.rules.ContractRuleExecutor;
import io.apicurio.registry.contracts.rules.ContractRuleResult;
import io.apicurio.registry.contracts.rules.RuleDefinition;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Per-field CEL rule executor that applies expressions to specific fields matched by schema tags.
 * <p>
 * Notes:
 * <ul>
 *   <li>Upstream conversion is assumed; field values are expected to be standard Java objects
 *       (Map, List, String, Number, Boolean, etc.).</li>
 *   <li>Null or empty {@code fieldTags} in context will result in a vacuous pass (no-op success).
 *       This is expected for client-side SerDe evaluation paths where server-side tag extraction is unavailable.</li>
 * </ul>
 */
@ApplicationScoped
public class CelFieldRuleExecutor implements ContractRuleExecutor {

    private static final Logger log = LoggerFactory.getLogger(CelFieldRuleExecutor.class);

    @Inject
    CelExpressionEvaluator evaluator;

    public CelFieldRuleExecutor() {
    }

    public CelFieldRuleExecutor(CelExpressionEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    @Override
    public String getRuleType() {
        return "CEL_FIELD";
    }

    @Override
    public ContractRuleResult execute(ContractRuleContext context) {
        RuleDefinition rule = context.getRule();
        String expr = rule.getExpr();
        if (expr == null || expr.isBlank()) {
            return ContractRuleResult.pass();
        }

        Set<String> ruleTags = rule.getTags();
        Map<String, Set<String>> fieldTags = context.getFieldTags();

        if (ruleTags == null || ruleTags.isEmpty() || fieldTags == null || fieldTags.isEmpty()) {
            log.debug("CEL_FIELD rule '{}' skipped: no tags in rule or no field tags available", rule.getName());
            return ContractRuleResult.pass();
        }

        Set<String> matchedFields = filterMatchedFields(fieldTags, ruleTags);
        if (matchedFields.isEmpty()) {
            log.debug("CEL_FIELD rule '{}' matched 0 fields — skipping", rule.getName());
            return ContractRuleResult.pass();
        }

        Map<String, Object> dataRecord = context.getRecord();
        if (dataRecord == null || dataRecord.isEmpty()) {
            return ContractRuleResult.pass();
        }

        try {
            if (rule.isCondition()) {
                List<String> failures = new ArrayList<>();
                evaluateConditionRecursively("", dataRecord, matchedFields, rule, failures);
                if (!failures.isEmpty()) {
                    String failureMsg = "Field validation failed for rule '" + rule.getName() + "': "
                            + String.join("; ", failures);
                    return ContractRuleResult.fail(failureMsg, rule.getOnFailure());
                }
                return ContractRuleResult.pass();
            } else if (rule.isTransform()) {
                Map<String, Object> mutableRecord = deepCopyMap(dataRecord);
                List<String> failures = new ArrayList<>();
                evaluateTransformRecursively("", mutableRecord, matchedFields, rule, failures);
                if (!failures.isEmpty()) {
                    String failureMsg = "Field transform failed for rule '" + rule.getName() + "': "
                            + String.join("; ", failures);
                    return ContractRuleResult.fail(failureMsg, rule.getOnFailure());
                }
                return ContractRuleResult.transform(mutableRecord);
            }
            return ContractRuleResult.pass();
        } catch (Exception e) {
            log.warn("CEL_FIELD evaluation failed for rule {}: {}", rule.getName(), e.getMessage());
            return ContractRuleResult.fail("CEL_FIELD evaluation error: " + e.getMessage(), rule.getOnFailure());
        }
    }

    private Set<String> filterMatchedFields(Map<String, Set<String>> fieldTags, Set<String> ruleTags) {
        Set<String> matched = new HashSet<>();
        for (Map.Entry<String, Set<String>> entry : fieldTags.entrySet()) {
            String path = entry.getKey();
            Set<String> tagsForField = entry.getValue();
            if (tagsForField != null && !Collections.disjoint(ruleTags, tagsForField)) {
                matched.add(path);
            }
        }
        return matched;
    }

    private boolean matchesFieldPath(String payloadPath, Set<String> matchedFields) {
        if (payloadPath == null || matchedFields == null || matchedFields.isEmpty()) {
            return false;
        }
        if (matchedFields.contains(payloadPath)) {
            return true;
        }
        String cleanPayload = payloadPath.replace("[]", "");
        for (String matched : matchedFields) {
            String cleanMatched = matched.replace("[]", "");
            if (cleanPayload.equals(cleanMatched)) {
                return true;
            }
            if (cleanMatched.endsWith(".values")) {
                String mapPrefix = cleanMatched.substring(0, cleanMatched.length() - ".values".length());
                if (cleanPayload.startsWith(mapPrefix + ".")) {
                    String rest = cleanPayload.substring(mapPrefix.length() + 1);
                    if (rest.indexOf('.') < 0) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void evaluateConditionRecursively(
            String currentPath,
            Object currentObj,
            Set<String> matchedFields,
            RuleDefinition rule,
            List<String> failures) {

        if (currentObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) currentObj;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                String childPath = currentPath.isEmpty() ? entry.getKey() : currentPath + "." + entry.getKey();
                evaluateConditionRecursively(childPath, entry.getValue(), matchedFields, rule, failures);
            }
        } else if (currentObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) currentObj;
            String listPath = currentPath + "[]";
            for (Object element : list) {
                evaluateConditionRecursively(listPath, element, matchedFields, rule, failures);
            }
        } else {
            // Leaf node value (including null)
            if (matchesFieldPath(currentPath, matchedFields)) {
                evaluateFieldCondition(currentPath, currentObj, rule, failures);
            }
        }
    }

    private void evaluateFieldCondition(
            String fieldPath,
            Object value,
            RuleDefinition rule,
            List<String> failures) {

        String leafName = getLeafName(fieldPath);
        Map<String, Object> variables = new HashMap<>();
        variables.put("value", value);
        variables.put("name", leafName);
        variables.put("fullName", fieldPath);

        try {
            Object evalResult = evaluator.evaluate(rule.getExpr(), variables);
            if (!Boolean.TRUE.equals(evalResult)) {
                failures.add("Field '" + fieldPath + "' (value: " + value + ") condition not met");
            }
        } catch (Exception e) {
            failures.add("Field '" + fieldPath + "' evaluation error: " + e.getMessage());
        }
    }

    private void evaluateTransformRecursively(
            String currentPath,
            Object currentObj,
            Set<String> matchedFields,
            RuleDefinition rule,
            List<String> failures) {

        if (currentObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) currentObj;
            transformMap(currentPath, map, matchedFields, rule, failures);
        } else if (currentObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) currentObj;
            transformList(currentPath, list, matchedFields, rule, failures);
        }
    }

    private void transformMap(
            String currentPath,
            Map<String, Object> map,
            Set<String> matchedFields,
            RuleDefinition rule,
            List<String> failures) {

        List<String> keys = new ArrayList<>(map.keySet());
        for (String key : keys) {
            String childPath = currentPath.isEmpty() ? key : currentPath + "." + key;
            Object value = map.get(key);
            if (!(value instanceof Map) && !(value instanceof List)) {
                if (matchesFieldPath(childPath, matchedFields)) {
                    map.put(key, evaluateFieldTransform(childPath, value, rule, failures));
                }
            } else {
                evaluateTransformRecursively(childPath, value, matchedFields, rule, failures);
            }
        }
    }

    private void transformList(
            String currentPath,
            List<Object> list,
            Set<String> matchedFields,
            RuleDefinition rule,
            List<String> failures) {

        String listPath = currentPath + "[]";
        for (int i = 0; i < list.size(); i++) {
            Object element = list.get(i);
            if (!(element instanceof Map) && !(element instanceof List)) {
                if (matchesFieldPath(listPath, matchedFields)) {
                    list.set(i, evaluateFieldTransform(listPath, element, rule, failures));
                }
            } else {
                evaluateTransformRecursively(listPath, element, matchedFields, rule, failures);
            }
        }
    }

    private Object evaluateFieldTransform(
            String fieldPath,
            Object value,
            RuleDefinition rule,
            List<String> failures) {

        String leafName = getLeafName(fieldPath);
        Map<String, Object> variables = new HashMap<>();
        variables.put("value", value);
        variables.put("name", leafName);
        variables.put("fullName", fieldPath);

        try {
            return evaluator.evaluate(rule.getExpr(), variables);
        } catch (Exception e) {
            log.warn("Field transform failed for path '{}': {}", fieldPath, e.getMessage());
            failures.add("Field transform failed for path '" + fieldPath + "': " + e.getMessage());
            return value;
        }
    }

    private String getLeafName(String path) {
        String cleanPath = path.replace("[]", "");
        int lastDot = cleanPath.lastIndexOf('.');
        return lastDot >= 0 ? cleanPath.substring(lastDot + 1) : cleanPath;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> deepCopyMap(Map<String, Object> original) {
        if (original == null) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : original.entrySet()) {
            copy.put(entry.getKey(), deepCopyValue(entry.getValue()));
        }
        return copy;
    }

    @SuppressWarnings("unchecked")
    private Object deepCopyValue(Object value) {
        if (value instanceof Map) {
            return deepCopyMap((Map<String, Object>) value);
        } else if (value instanceof List) {
            List<Object> listCopy = new ArrayList<>();
            for (Object item : (List<Object>) value) {
                listCopy.add(deepCopyValue(item));
            }
            return listCopy;
        }
        return value;
    }
}
