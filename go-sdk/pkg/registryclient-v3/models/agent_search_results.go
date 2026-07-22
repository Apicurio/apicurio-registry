package models

import (
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
)

// AgentSearchResults search results for agent cards.
type AgentSearchResults struct {
    // Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
    additionalData map[string]any
    // The agent search results.
    agents []AgentSearchResultable
    // The total number of agents matching the query.
    count *int32
}
// NewAgentSearchResults instantiates a new AgentSearchResults and sets the default values.
func NewAgentSearchResults()(*AgentSearchResults) {
    m := &AgentSearchResults{
    }
    m.SetAdditionalData(make(map[string]any))
    return m
}
// CreateAgentSearchResultsFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateAgentSearchResultsFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
    return NewAgentSearchResults(), nil
}
// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *AgentSearchResults) GetAdditionalData()(map[string]any) {
    return m.additionalData
}
// GetAgents gets the agents property value. The agent search results.
// returns a []AgentSearchResultable when successful
func (m *AgentSearchResults) GetAgents()([]AgentSearchResultable) {
    return m.agents
}
// GetCount gets the count property value. The total number of agents matching the query.
// returns a *int32 when successful
func (m *AgentSearchResults) GetCount()(*int32) {
    return m.count
}
// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *AgentSearchResults) GetFieldDeserializers()(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error)) {
    res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error))
    res["agents"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetCollectionOfObjectValues(CreateAgentSearchResultFromDiscriminatorValue)
        if err != nil {
            return err
        }
        if val != nil {
            res := make([]AgentSearchResultable, len(val))
            for i, v := range val {
                if v != nil {
                    res[i] = v.(AgentSearchResultable)
                }
            }
            m.SetAgents(res)
        }
        return nil
    }
    res["count"] = func (n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
        val, err := n.GetInt32Value()
        if err != nil {
            return err
        }
        if val != nil {
            m.SetCount(val)
        }
        return nil
    }
    return res
}
// Serialize serializes information the current object
func (m *AgentSearchResults) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter)(error) {
    if m.GetAgents() != nil {
        cast := make([]i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, len(m.GetAgents()))
        for i, v := range m.GetAgents() {
            if v != nil {
                cast[i] = v.(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable)
            }
        }
        err := writer.WriteCollectionOfObjectValues("agents", cast)
        if err != nil {
            return err
        }
    }
    {
        err := writer.WriteInt32Value("count", m.GetCount())
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
func (m *AgentSearchResults) SetAdditionalData(value map[string]any)() {
    m.additionalData = value
}
// SetAgents sets the agents property value. The agent search results.
func (m *AgentSearchResults) SetAgents(value []AgentSearchResultable)() {
    m.agents = value
}
// SetCount sets the count property value. The total number of agents matching the query.
func (m *AgentSearchResults) SetCount(value *int32)() {
    m.count = value
}
type AgentSearchResultsable interface {
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
    GetAgents()([]AgentSearchResultable)
    GetCount()(*int32)
    SetAgents(value []AgentSearchResultable)()
    SetCount(value *int32)()
}
