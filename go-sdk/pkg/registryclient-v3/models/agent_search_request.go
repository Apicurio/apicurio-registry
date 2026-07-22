package models

import (
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// AgentSearchRequest request body for the advanced agent search endpoint.
type AgentSearchRequest struct {
    // Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
    additionalData map[string]any
    // Structured filters for advanced agent search.
    filters AgentSearchFiltersable
    // Maximum number of results to return.
    limit *int32
    // Number of results to skip.
    offset *int32
    // Free-text search query.
    query *string
}
// NewAgentSearchRequest instantiates a new AgentSearchRequest and sets the default values.
func NewAgentSearchRequest()(*AgentSearchRequest) {
    m := &AgentSearchRequest{
    }
    m.SetAdditionalData(make(map[string]any))
    return m
}
// CreateAgentSearchRequestFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateAgentSearchRequestFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
    return NewAgentSearchRequest(), nil
}
// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *AgentSearchRequest) GetAdditionalData()(map[string]any) {
    return m.additionalData
}
// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *AgentSearchRequest) GetFieldDeserializers()(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error)) {
    res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error))
    res["filters"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetObjectValue(CreateAgentSearchFiltersFromDiscriminatorValue)
        if err != nil {
            return err
        }
        if val != nil {
            m.SetFilters(val.(AgentSearchFiltersable))
        }
        return nil
    }
    res["limit"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetInt32Value()
        if err != nil {
            return err
        }
        if val != nil {
            m.SetLimit(val)
        }
        return nil
    }
    res["offset"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetInt32Value()
        if err != nil {
            return err
        }
        if val != nil {
            m.SetOffset(val)
        }
        return nil
    }
    res["query"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetStringValue()
        if err != nil {
            return err
        }
        if val != nil {
            m.SetQuery(val)
        }
        return nil
    }
    return res
}
// GetFilters gets the filters property value. Structured filters for advanced agent search.
// returns a AgentSearchFiltersable when successful
func (m *AgentSearchRequest) GetFilters()(AgentSearchFiltersable) {
    return m.filters
}
// GetLimit gets the limit property value. Maximum number of results to return.
// returns a *int32 when successful
func (m *AgentSearchRequest) GetLimit()(*int32) {
    return m.limit
}
// GetOffset gets the offset property value. Number of results to skip.
// returns a *int32 when successful
func (m *AgentSearchRequest) GetOffset()(*int32) {
    return m.offset
}
// GetQuery gets the query property value. Free-text search query.
// returns a *string when successful
func (m *AgentSearchRequest) GetQuery()(*string) {
    return m.query
}
// Serialize serializes information the current object
func (m *AgentSearchRequest) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter)(error) {
    {
        err := writer.WriteObjectValue("filters", m.GetFilters())
        if err != nil {
            return err
        }
    }
    {
        err := writer.WriteInt32Value("limit", m.GetLimit())
        if err != nil {
            return err
        }
    }
    {
        err := writer.WriteInt32Value("offset", m.GetOffset())
        if err != nil {
            return err
        }
    }
    {
        err := writer.WriteStringValue("query", m.GetQuery())
        if err != nil {
            return err
        }
    }
    {
        err := writer.WriteAdditionalData(m.GetAdditionalData())
        if err != nil {
            return err
        }
    }
    return nil
}
// SetAdditionalData sets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
func (m *AgentSearchRequest) SetAdditionalData(value map[string]any)() {
    m.additionalData = value
}
// SetFilters sets the filters property value. Structured filters for advanced agent search.
func (m *AgentSearchRequest) SetFilters(value AgentSearchFiltersable)() {
    m.filters = value
}
// SetLimit sets the limit property value. Maximum number of results to return.
func (m *AgentSearchRequest) SetLimit(value *int32)() {
    m.limit = value
}
// SetOffset sets the offset property value. Number of results to skip.
func (m *AgentSearchRequest) SetOffset(value *int32)() {
    m.offset = value
}
// SetQuery sets the query property value. Free-text search query.
func (m *AgentSearchRequest) SetQuery(value *string)() {
    m.query = value
}
type AgentSearchRequestable interface {
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
    GetFilters()(AgentSearchFiltersable)
    GetLimit()(*int32)
    GetOffset()(*int32)
    GetQuery()(*string)
    SetFilters(value AgentSearchFiltersable)()
    SetLimit(value *int32)()
    SetOffset(value *int32)()
    SetQuery(value *string)()
}
