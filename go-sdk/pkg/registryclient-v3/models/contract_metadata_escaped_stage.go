package models

// Promotion stage.
type ContractMetadata_stage int

const (
	DEV_CONTRACTMETADATA_STAGE ContractMetadata_stage = iota
	STAGE_CONTRACTMETADATA_STAGE
	PROD_CONTRACTMETADATA_STAGE
)

func (i ContractMetadata_stage) String() string {
	return []string{"DEV", "STAGE", "PROD"}[i]
}
func ParseContractMetadata_stage(v string) (any, error) {
	result := DEV_CONTRACTMETADATA_STAGE
	switch v {
	case "DEV":
		result = DEV_CONTRACTMETADATA_STAGE
	case "STAGE":
		result = STAGE_CONTRACTMETADATA_STAGE
	case "PROD":
		result = PROD_CONTRACTMETADATA_STAGE
	default:
		return nil, nil
	}
	return &result, nil
}
func SerializeContractMetadata_stage(values []ContractMetadata_stage) []string {
	result := make([]string, len(values))
	for i, v := range values {
		result[i] = v.String()
	}
	return result
}
func (i ContractMetadata_stage) isMultiValue() bool {
	return false
}
