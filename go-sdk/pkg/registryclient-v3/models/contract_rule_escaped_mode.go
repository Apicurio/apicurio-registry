package models

// When the rule is applied.
type ContractRule_mode int

const (
	WRITE_CONTRACTRULE_MODE ContractRule_mode = iota
	READ_CONTRACTRULE_MODE
	WRITEREAD_CONTRACTRULE_MODE
	UPGRADE_CONTRACTRULE_MODE
	DOWNGRADE_CONTRACTRULE_MODE
)

func (i ContractRule_mode) String() string {
	return []string{"WRITE", "READ", "WRITEREAD", "UPGRADE", "DOWNGRADE"}[i]
}
func ParseContractRule_mode(v string) (any, error) {
	result := WRITE_CONTRACTRULE_MODE
	switch v {
	case "WRITE":
		result = WRITE_CONTRACTRULE_MODE
	case "READ":
		result = READ_CONTRACTRULE_MODE
	case "WRITEREAD":
		result = WRITEREAD_CONTRACTRULE_MODE
	case "UPGRADE":
		result = UPGRADE_CONTRACTRULE_MODE
	case "DOWNGRADE":
		result = DOWNGRADE_CONTRACTRULE_MODE
	default:
		return nil, nil
	}
	return &result, nil
}
func SerializeContractRule_mode(values []ContractRule_mode) []string {
	result := make([]string, len(values))
	for i, v := range values {
		result[i] = v.String()
	}
	return result
}
func (i ContractRule_mode) isMultiValue() bool {
	return false
}
