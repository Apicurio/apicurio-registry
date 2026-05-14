package models

// The contract lifecycle status.
type ContractMetadata_status int

const (
	DRAFT_CONTRACTMETADATA_STATUS ContractMetadata_status = iota
	STABLE_CONTRACTMETADATA_STATUS
	DEPRECATED_CONTRACTMETADATA_STATUS
)

func (i ContractMetadata_status) String() string {
	return []string{"DRAFT", "STABLE", "DEPRECATED"}[i]
}
func ParseContractMetadata_status(v string) (any, error) {
	result := DRAFT_CONTRACTMETADATA_STATUS
	switch v {
	case "DRAFT":
		result = DRAFT_CONTRACTMETADATA_STATUS
	case "STABLE":
		result = STABLE_CONTRACTMETADATA_STATUS
	case "DEPRECATED":
		result = DEPRECATED_CONTRACTMETADATA_STATUS
	default:
		return nil, nil
	}
	return &result, nil
}
func SerializeContractMetadata_status(values []ContractMetadata_status) []string {
	result := make([]string, len(values))
	for i, v := range values {
		result[i] = v.String()
	}
	return result
}
func (i ContractMetadata_status) isMultiValue() bool {
	return false
}
