package contracts
// Search for contracts.
type GetOrderbyQueryParameterType int

const (
    NAME_GETORDERBYQUERYPARAMETERTYPE GetOrderbyQueryParameterType = iota
    CREATEDON_GETORDERBYQUERYPARAMETERTYPE
    MODIFIEDON_GETORDERBYQUERYPARAMETERTYPE
)

func (i GetOrderbyQueryParameterType) String() string {
    return []string{"name", "createdOn", "modifiedOn"}[i]
}
func ParseGetOrderbyQueryParameterType(v string) (any, error) {
    result := NAME_GETORDERBYQUERYPARAMETERTYPE
    switch v {
        case "name":
            result = NAME_GETORDERBYQUERYPARAMETERTYPE
        case "createdOn":
            result = CREATEDON_GETORDERBYQUERYPARAMETERTYPE
        case "modifiedOn":
            result = MODIFIEDON_GETORDERBYQUERYPARAMETERTYPE
        default:
            return nil, nil
    }
    return &result, nil
}
func SerializeGetOrderbyQueryParameterType(values []GetOrderbyQueryParameterType) []string {
    result := make([]string, len(values))
    for i, v := range values {
        result[i] = v.String()
    }
    return result
}
func (i GetOrderbyQueryParameterType) isMultiValue() bool {
    return false
}
