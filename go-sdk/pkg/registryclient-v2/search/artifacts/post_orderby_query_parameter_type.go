package artifacts
import (
    "errors"
)
// Search for artifacts in the registry.
type PostOrderbyQueryParameterType int

const (
    NAME_POSTORDERBYQUERYPARAMETERTYPE PostOrderbyQueryParameterType = iota
    CREATEDON_POSTORDERBYQUERYPARAMETERTYPE
)

func (i PostOrderbyQueryParameterType) String() string {
    return []string{"name", "createdOn"}[i]
}
func ParsePostOrderbyQueryParameterType(v string) (any, error) {
    result := NAME_POSTORDERBYQUERYPARAMETERTYPE
    switch v {
        case "name":
            result = NAME_POSTORDERBYQUERYPARAMETERTYPE
        case "createdOn":
            result = CREATEDON_POSTORDERBYQUERYPARAMETERTYPE
        default:
            return 0, errors.New("Unknown PostOrderbyQueryParameterType value: " + v)
    }
    return &result, nil
}
func SerializePostOrderbyQueryParameterType(values []PostOrderbyQueryParameterType) []string {
    result := make([]string, len(values))
    for i, v := range values {
        result[i] = v.String()
    }
    return result
}
func (i PostOrderbyQueryParameterType) isMultiValue() bool {
    return false
}
