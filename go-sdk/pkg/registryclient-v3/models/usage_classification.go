package models

// Classification of schema usage based on last fetch time.
type UsageClassification int

const (
	ACTIVE_USAGECLASSIFICATION UsageClassification = iota
	STALE_USAGECLASSIFICATION
	DEAD_USAGECLASSIFICATION
)

func (i UsageClassification) String() string {
	return []string{"ACTIVE", "STALE", "DEAD"}[i]
}
func ParseUsageClassification(v string) (any, error) {
	result := ACTIVE_USAGECLASSIFICATION
	switch v {
	case "ACTIVE":
		result = ACTIVE_USAGECLASSIFICATION
	case "STALE":
		result = STALE_USAGECLASSIFICATION
	case "DEAD":
		result = DEAD_USAGECLASSIFICATION
	default:
		return nil, nil
	}
	return &result, nil
}
func SerializeUsageClassification(values []UsageClassification) []string {
	result := make([]string, len(values))
	for i, v := range values {
		result[i] = v.String()
	}
	return result
}
func (i UsageClassification) isMultiValue() bool {
	return false
}
