package models

// The rule category (DOMAIN or MIGRATION).
type ContractRuleSearchResult_ruleCategory int

const (
	DOMAIN_CONTRACTRULESEARCHRESULT_RULECATEGORY ContractRuleSearchResult_ruleCategory = iota
	MIGRATION_CONTRACTRULESEARCHRESULT_RULECATEGORY
)

func (i ContractRuleSearchResult_ruleCategory) String() string {
	return []string{"DOMAIN", "MIGRATION"}[i]
}
func ParseContractRuleSearchResult_ruleCategory(v string) (any, error) {
	result := DOMAIN_CONTRACTRULESEARCHRESULT_RULECATEGORY
	switch v {
	case "DOMAIN":
		result = DOMAIN_CONTRACTRULESEARCHRESULT_RULECATEGORY
	case "MIGRATION":
		result = MIGRATION_CONTRACTRULESEARCHRESULT_RULECATEGORY
	default:
		return nil, nil
	}
	return &result, nil
}
func SerializeContractRuleSearchResult_ruleCategory(values []ContractRuleSearchResult_ruleCategory) []string {
	result := make([]string, len(values))
	for i, v := range values {
		result[i] = v.String()
	}
	return result
}
func (i ContractRuleSearchResult_ruleCategory) isMultiValue() bool {
	return false
}
