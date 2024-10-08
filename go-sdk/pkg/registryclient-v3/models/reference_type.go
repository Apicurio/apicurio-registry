package models

type ReferenceType int

const (
	OUTBOUND_REFERENCETYPE ReferenceType = iota
	INBOUND_REFERENCETYPE
)

func (i ReferenceType) String() string {
	return []string{"OUTBOUND", "INBOUND"}[i]
}
func ParseReferenceType(v string) (any, error) {
	result := OUTBOUND_REFERENCETYPE
	switch v {
	case "OUTBOUND":
		result = OUTBOUND_REFERENCETYPE
	case "INBOUND":
		result = INBOUND_REFERENCETYPE
	default:
		return nil, nil
	}
	return &result, nil
}
func SerializeReferenceType(values []ReferenceType) []string {
	result := make([]string, len(values))
	for i, v := range values {
		result[i] = v.String()
	}
	return result
}
func (i ReferenceType) isMultiValue() bool {
	return false
}
