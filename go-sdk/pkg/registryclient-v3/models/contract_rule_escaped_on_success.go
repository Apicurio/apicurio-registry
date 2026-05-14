package models

// Action on rule success.
type ContractRule_onSuccess int

const (
	NONE_CONTRACTRULE_ONSUCCESS ContractRule_onSuccess = iota
	ERROR_CONTRACTRULE_ONSUCCESS
	DLQ_CONTRACTRULE_ONSUCCESS
)

func (i ContractRule_onSuccess) String() string {
	return []string{"NONE", "ERROR", "DLQ"}[i]
}
func ParseContractRule_onSuccess(v string) (any, error) {
	result := NONE_CONTRACTRULE_ONSUCCESS
	switch v {
	case "NONE":
		result = NONE_CONTRACTRULE_ONSUCCESS
	case "ERROR":
		result = ERROR_CONTRACTRULE_ONSUCCESS
	case "DLQ":
		result = DLQ_CONTRACTRULE_ONSUCCESS
	default:
		return nil, nil
	}
	return &result, nil
}
func SerializeContractRule_onSuccess(values []ContractRule_onSuccess) []string {
	result := make([]string, len(values))
	for i, v := range values {
		result[i] = v.String()
	}
	return result
}
func (i ContractRule_onSuccess) isMultiValue() bool {
	return false
}
