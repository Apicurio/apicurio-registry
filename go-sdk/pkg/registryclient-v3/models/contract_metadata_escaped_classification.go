package models

// Data classification level.
type ContractMetadata_classification int

const (
	PUBLIC_CONTRACTMETADATA_CLASSIFICATION ContractMetadata_classification = iota
	INTERNAL_CONTRACTMETADATA_CLASSIFICATION
	CONFIDENTIAL_CONTRACTMETADATA_CLASSIFICATION
	RESTRICTED_CONTRACTMETADATA_CLASSIFICATION
)

func (i ContractMetadata_classification) String() string {
	return []string{"PUBLIC", "INTERNAL", "CONFIDENTIAL", "RESTRICTED"}[i]
}
func ParseContractMetadata_classification(v string) (any, error) {
	result := PUBLIC_CONTRACTMETADATA_CLASSIFICATION
	switch v {
	case "PUBLIC":
		result = PUBLIC_CONTRACTMETADATA_CLASSIFICATION
	case "INTERNAL":
		result = INTERNAL_CONTRACTMETADATA_CLASSIFICATION
	case "CONFIDENTIAL":
		result = CONFIDENTIAL_CONTRACTMETADATA_CLASSIFICATION
	case "RESTRICTED":
		result = RESTRICTED_CONTRACTMETADATA_CLASSIFICATION
	default:
		return nil, nil
	}
	return &result, nil
}
func SerializeContractMetadata_classification(values []ContractMetadata_classification) []string {
	result := make([]string, len(values))
	for i, v := range values {
		result[i] = v.String()
	}
	return result
}
func (i ContractMetadata_classification) isMultiValue() bool {
	return false
}
