package models

// The target lifecycle status. Valid transitions: DRAFT to STABLE, DRAFT to DEPRECATED, STABLE to DEPRECATED.
type ContractStatusTransition_status int

const (
	DRAFT_CONTRACTSTATUSTRANSITION_STATUS ContractStatusTransition_status = iota
	STABLE_CONTRACTSTATUSTRANSITION_STATUS
	DEPRECATED_CONTRACTSTATUSTRANSITION_STATUS
)

func (i ContractStatusTransition_status) String() string {
	return []string{"DRAFT", "STABLE", "DEPRECATED"}[i]
}
func ParseContractStatusTransition_status(v string) (any, error) {
	result := DRAFT_CONTRACTSTATUSTRANSITION_STATUS
	switch v {
	case "DRAFT":
		result = DRAFT_CONTRACTSTATUSTRANSITION_STATUS
	case "STABLE":
		result = STABLE_CONTRACTSTATUSTRANSITION_STATUS
	case "DEPRECATED":
		result = DEPRECATED_CONTRACTSTATUSTRANSITION_STATUS
	default:
		return nil, nil
	}
	return &result, nil
}
func SerializeContractStatusTransition_status(values []ContractStatusTransition_status) []string {
	result := make([]string, len(values))
	for i, v := range values {
		result[i] = v.String()
	}
	return result
}
func (i ContractStatusTransition_status) isMultiValue() bool {
	return false
}
