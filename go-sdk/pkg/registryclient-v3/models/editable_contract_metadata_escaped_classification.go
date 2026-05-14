package models

// Data classification level.
type EditableContractMetadata_classification int

const (
	PUBLIC_EDITABLECONTRACTMETADATA_CLASSIFICATION EditableContractMetadata_classification = iota
	INTERNAL_EDITABLECONTRACTMETADATA_CLASSIFICATION
	CONFIDENTIAL_EDITABLECONTRACTMETADATA_CLASSIFICATION
	RESTRICTED_EDITABLECONTRACTMETADATA_CLASSIFICATION
)

func (i EditableContractMetadata_classification) String() string {
	return []string{"PUBLIC", "INTERNAL", "CONFIDENTIAL", "RESTRICTED"}[i]
}
func ParseEditableContractMetadata_classification(v string) (any, error) {
	result := PUBLIC_EDITABLECONTRACTMETADATA_CLASSIFICATION
	switch v {
	case "PUBLIC":
		result = PUBLIC_EDITABLECONTRACTMETADATA_CLASSIFICATION
	case "INTERNAL":
		result = INTERNAL_EDITABLECONTRACTMETADATA_CLASSIFICATION
	case "CONFIDENTIAL":
		result = CONFIDENTIAL_EDITABLECONTRACTMETADATA_CLASSIFICATION
	case "RESTRICTED":
		result = RESTRICTED_EDITABLECONTRACTMETADATA_CLASSIFICATION
	default:
		return nil, nil
	}
	return &result, nil
}
func SerializeEditableContractMetadata_classification(values []EditableContractMetadata_classification) []string {
	result := make([]string, len(values))
	for i, v := range values {
		result[i] = v.String()
	}
	return result
}
func (i EditableContractMetadata_classification) isMultiValue() bool {
	return false
}
