package models

// Action on rule failure.
type ContractRule_onFailure int

const (
	NONE_CONTRACTRULE_ONFAILURE ContractRule_onFailure = iota
	ERROR_CONTRACTRULE_ONFAILURE
	DLQ_CONTRACTRULE_ONFAILURE
)

func (i ContractRule_onFailure) String() string {
	return []string{"NONE", "ERROR", "DLQ"}[i]
}
func ParseContractRule_onFailure(v string) (any, error) {
	result := NONE_CONTRACTRULE_ONFAILURE
	switch v {
	case "NONE":
		result = NONE_CONTRACTRULE_ONFAILURE
	case "ERROR":
		result = ERROR_CONTRACTRULE_ONFAILURE
	case "DLQ":
		result = DLQ_CONTRACTRULE_ONFAILURE
	default:
		return nil, nil
	}
	return &result, nil
}
func SerializeContractRule_onFailure(values []ContractRule_onFailure) []string {
	result := make([]string, len(values))
	for i, v := range values {
		result[i] = v.String()
	}
	return result
}
func (i ContractRule_onFailure) isMultiValue() bool {
	return false
}
