package execute
// Execute contract rules against a data record.
type ExecutePostRequestBody_mode int

const (
    WRITE_EXECUTEPOSTREQUESTBODY_MODE ExecutePostRequestBody_mode = iota
    READ_EXECUTEPOSTREQUESTBODY_MODE
)

func (i ExecutePostRequestBody_mode) String() string {
    return []string{"WRITE", "READ"}[i]
}
func ParseExecutePostRequestBody_mode(v string) (any, error) {
    result := WRITE_EXECUTEPOSTREQUESTBODY_MODE
    switch v {
        case "WRITE":
            result = WRITE_EXECUTEPOSTREQUESTBODY_MODE
        case "READ":
            result = READ_EXECUTEPOSTREQUESTBODY_MODE
        default:
            return nil, nil
    }
    return &result, nil
}
func SerializeExecutePostRequestBody_mode(values []ExecutePostRequestBody_mode) []string {
    result := make([]string, len(values))
    for i, v := range values {
        result[i] = v.String()
    }
    return result
}
func (i ExecutePostRequestBody_mode) isMultiValue() bool {
    return false
}
