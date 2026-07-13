package promote
// Promote a contract to the next deployment stage.
type PromotePostRequestBody_targetStage int

const (
    DEV_PROMOTEPOSTREQUESTBODY_TARGETSTAGE PromotePostRequestBody_targetStage = iota
    STAGE_PROMOTEPOSTREQUESTBODY_TARGETSTAGE
    PROD_PROMOTEPOSTREQUESTBODY_TARGETSTAGE
)

func (i PromotePostRequestBody_targetStage) String() string {
    return []string{"DEV", "STAGE", "PROD"}[i]
}
func ParsePromotePostRequestBody_targetStage(v string) (any, error) {
    result := DEV_PROMOTEPOSTREQUESTBODY_TARGETSTAGE
    switch v {
        case "DEV":
            result = DEV_PROMOTEPOSTREQUESTBODY_TARGETSTAGE
        case "STAGE":
            result = STAGE_PROMOTEPOSTREQUESTBODY_TARGETSTAGE
        case "PROD":
            result = PROD_PROMOTEPOSTREQUESTBODY_TARGETSTAGE
        default:
            return nil, nil
    }
    return &result, nil
}
func SerializePromotePostRequestBody_targetStage(values []PromotePostRequestBody_targetStage) []string {
    result := make([]string, len(values))
    for i, v := range values {
        result[i] = v.String()
    }
    return result
}
func (i PromotePostRequestBody_targetStage) isMultiValue() bool {
    return false
}
