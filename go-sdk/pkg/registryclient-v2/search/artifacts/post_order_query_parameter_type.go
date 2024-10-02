package artifacts

// Search for artifacts in the registry.
type PostOrderQueryParameterType int

const (
	ASC_POSTORDERQUERYPARAMETERTYPE PostOrderQueryParameterType = iota
	DESC_POSTORDERQUERYPARAMETERTYPE
)

func (i PostOrderQueryParameterType) String() string {
	return []string{"asc", "desc"}[i]
}
func ParsePostOrderQueryParameterType(v string) (any, error) {
	result := ASC_POSTORDERQUERYPARAMETERTYPE
	switch v {
	case "asc":
		result = ASC_POSTORDERQUERYPARAMETERTYPE
	case "desc":
		result = DESC_POSTORDERQUERYPARAMETERTYPE
	default:
		return nil, nil
	}
	return &result, nil
}
func SerializePostOrderQueryParameterType(values []PostOrderQueryParameterType) []string {
	result := make([]string, len(values))
	for i, v := range values {
		result[i] = v.String()
	}
	return result
}
func (i PostOrderQueryParameterType) isMultiValue() bool {
	return false
}
