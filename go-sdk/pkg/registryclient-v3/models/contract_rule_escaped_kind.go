package models

// The rule kind.
type ContractRule_kind int

const (
	CONDITION_CONTRACTRULE_KIND ContractRule_kind = iota
	TRANSFORM_CONTRACTRULE_KIND
)

func (i ContractRule_kind) String() string {
	return []string{"CONDITION", "TRANSFORM"}[i]
}
func ParseContractRule_kind(v string) (any, error) {
	result := CONDITION_CONTRACTRULE_KIND
	switch v {
	case "CONDITION":
		result = CONDITION_CONTRACTRULE_KIND
	case "TRANSFORM":
		result = TRANSFORM_CONTRACTRULE_KIND
	default:
		return nil, nil
	}
	return &result, nil
}
func SerializeContractRule_kind(values []ContractRule_kind) []string {
	result := make([]string, len(values))
	for i, v := range values {
		result[i] = v.String()
	}
	return result
}
func (i ContractRule_kind) isMultiValue() bool {
	return false
}
