package models

// The operation type (SERIALIZE or DESERIALIZE).
type SchemaUsageEvent_operation int

const (
	SERIALIZE_SCHEMAUSAGEEVENT_OPERATION SchemaUsageEvent_operation = iota
	DESERIALIZE_SCHEMAUSAGEEVENT_OPERATION
)

func (i SchemaUsageEvent_operation) String() string {
	return []string{"SERIALIZE", "DESERIALIZE"}[i]
}
func ParseSchemaUsageEvent_operation(v string) (any, error) {
	result := SERIALIZE_SCHEMAUSAGEEVENT_OPERATION
	switch v {
	case "SERIALIZE":
		result = SERIALIZE_SCHEMAUSAGEEVENT_OPERATION
	case "DESERIALIZE":
		result = DESERIALIZE_SCHEMAUSAGEEVENT_OPERATION
	default:
		return nil, nil
	}
	return &result, nil
}
func SerializeSchemaUsageEvent_operation(values []SchemaUsageEvent_operation) []string {
	result := make([]string, len(values))
	for i, v := range values {
		result[i] = v.String()
	}
	return result
}
func (i SchemaUsageEvent_operation) isMultiValue() bool {
	return false
}
