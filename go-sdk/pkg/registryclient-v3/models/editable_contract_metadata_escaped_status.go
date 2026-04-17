package models

// The contract lifecycle status.
type EditableContractMetadata_status int

const (
	DRAFT_EDITABLECONTRACTMETADATA_STATUS EditableContractMetadata_status = iota
	STABLE_EDITABLECONTRACTMETADATA_STATUS
	DEPRECATED_EDITABLECONTRACTMETADATA_STATUS
)

func (i EditableContractMetadata_status) String() string {
	return []string{"DRAFT", "STABLE", "DEPRECATED"}[i]
}
func ParseEditableContractMetadata_status(v string) (any, error) {
	result := DRAFT_EDITABLECONTRACTMETADATA_STATUS
	switch v {
	case "DRAFT":
		result = DRAFT_EDITABLECONTRACTMETADATA_STATUS
	case "STABLE":
		result = STABLE_EDITABLECONTRACTMETADATA_STATUS
	case "DEPRECATED":
		result = DEPRECATED_EDITABLECONTRACTMETADATA_STATUS
	default:
		return nil, nil
	}
	return &result, nil
}
func SerializeEditableContractMetadata_status(values []EditableContractMetadata_status) []string {
	result := make([]string, len(values))
	for i, v := range values {
		result[i] = v.String()
	}
	return result
}
func (i EditableContractMetadata_status) isMultiValue() bool {
	return false
}
