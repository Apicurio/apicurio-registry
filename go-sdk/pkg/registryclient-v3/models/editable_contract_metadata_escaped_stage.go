package models

// Promotion stage.
type EditableContractMetadata_stage int

const (
	DEV_EDITABLECONTRACTMETADATA_STAGE EditableContractMetadata_stage = iota
	STAGE_EDITABLECONTRACTMETADATA_STAGE
	PROD_EDITABLECONTRACTMETADATA_STAGE
)

func (i EditableContractMetadata_stage) String() string {
	return []string{"DEV", "STAGE", "PROD"}[i]
}
func ParseEditableContractMetadata_stage(v string) (any, error) {
	result := DEV_EDITABLECONTRACTMETADATA_STAGE
	switch v {
	case "DEV":
		result = DEV_EDITABLECONTRACTMETADATA_STAGE
	case "STAGE":
		result = STAGE_EDITABLECONTRACTMETADATA_STAGE
	case "PROD":
		result = PROD_EDITABLECONTRACTMETADATA_STAGE
	default:
		return nil, nil
	}
	return &result, nil
}
func SerializeEditableContractMetadata_stage(values []EditableContractMetadata_stage) []string {
	result := make([]string, len(values))
	for i, v := range values {
		result[i] = v.String()
	}
	return result
}
func (i EditableContractMetadata_stage) isMultiValue() bool {
	return false
}
