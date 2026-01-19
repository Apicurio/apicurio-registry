package models

// The direction of references to include in the graph.
type ReferenceGraphDirection int

const (
	OUTBOUND_REFERENCEGRAPHDIRECTION ReferenceGraphDirection = iota
	INBOUND_REFERENCEGRAPHDIRECTION
	BOTH_REFERENCEGRAPHDIRECTION
)

func (i ReferenceGraphDirection) String() string {
	return []string{"OUTBOUND", "INBOUND", "BOTH"}[i]
}
func ParseReferenceGraphDirection(v string) (any, error) {
	result := OUTBOUND_REFERENCEGRAPHDIRECTION
	switch v {
	case "OUTBOUND":
		result = OUTBOUND_REFERENCEGRAPHDIRECTION
	case "INBOUND":
		result = INBOUND_REFERENCEGRAPHDIRECTION
	case "BOTH":
		result = BOTH_REFERENCEGRAPHDIRECTION
	default:
		return nil, nil
	}
	return &result, nil
}
func SerializeReferenceGraphDirection(values []ReferenceGraphDirection) []string {
	result := make([]string, len(values))
	for i, v := range values {
		result[i] = v.String()
	}
	return result
}
func (i ReferenceGraphDirection) isMultiValue() bool {
	return false
}
