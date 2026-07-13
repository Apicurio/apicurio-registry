package contracts
// Search for contracts.
type GetOrderQueryParameterType int

const (
    ASC_GETORDERQUERYPARAMETERTYPE GetOrderQueryParameterType = iota
    DESC_GETORDERQUERYPARAMETERTYPE
)

func (i GetOrderQueryParameterType) String() string {
    return []string{"asc", "desc"}[i]
}
func ParseGetOrderQueryParameterType(v string) (any, error) {
    result := ASC_GETORDERQUERYPARAMETERTYPE
    switch v {
        case "asc":
            result = ASC_GETORDERQUERYPARAMETERTYPE
        case "desc":
            result = DESC_GETORDERQUERYPARAMETERTYPE
        default:
            return nil, nil
    }
    return &result, nil
}
func SerializeGetOrderQueryParameterType(values []GetOrderQueryParameterType) []string {
    result := make([]string, len(values))
    for i, v := range values {
        result[i] = v.String()
    }
    return result
}
func (i GetOrderQueryParameterType) isMultiValue() bool {
    return false
}
