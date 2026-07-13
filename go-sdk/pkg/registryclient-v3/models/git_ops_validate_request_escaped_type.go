package models
// Validation type. Currently only `pull` is supported.
type GitOpsValidateRequest_type int

const (
    PULL_GITOPSVALIDATEREQUEST_TYPE GitOpsValidateRequest_type = iota
)

func (i GitOpsValidateRequest_type) String() string {
    return []string{"pull"}[i]
}
func ParseGitOpsValidateRequest_type(v string) (any, error) {
    result := PULL_GITOPSVALIDATEREQUEST_TYPE
    switch v {
        case "pull":
            result = PULL_GITOPSVALIDATEREQUEST_TYPE
        default:
            return nil, nil
    }
    return &result, nil
}
func SerializeGitOpsValidateRequest_type(values []GitOpsValidateRequest_type) []string {
    result := make([]string, len(values))
    for i, v := range values {
        result[i] = v.String()
    }
    return result
}
func (i GitOpsValidateRequest_type) isMultiValue() bool {
    return false
}
