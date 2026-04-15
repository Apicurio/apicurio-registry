package models

import (
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
	i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e "time"
)

// GitOpsStatus describes the current synchronization status of the GitOps storage backend. This includes the current sync state, the Git commit marker, load statistics, and any errors from the last sync attempt.
type GitOpsStatus struct {
	// Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
	additionalData map[string]any
	// Number of artifacts loaded in the last successful sync.
	artifactCount *int32
	// The Git commit SHA currently loaded in the storage. Null if no data has been loaded yet.
	currentMarker *string
	// Number of groups loaded in the last successful sync.
	groupCount *int32
	// List of error messages from the last failed load attempt. Empty if the last load was successful.
	lastErrors []string
	// ISO 8601 timestamp of the last successful synchronization.
	lastSuccessfulSync *i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time
	// ISO 8601 timestamp of the last synchronization attempt (successful or not).
	lastSyncAttempt *i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time
	// Per-source status. Maps source ID (e.g., repository ID) to its current marker (e.g., Git commit SHA). Only present when multiple sources are configured.
	sources GitOpsStatus_sourcesable
	// The current synchronization state of the GitOps storage. Possible values: INITIALIZING (first load not yet completed), IDLE (serving latest data), LOADING (sync in progress), SWITCHING (data loaded, waiting for write lock to publish), ERROR (last sync or switch failed, serving previous data).
	syncState *string
	// Number of artifact versions loaded in the last successful sync.
	versionCount *int32
}

// NewGitOpsStatus instantiates a new GitOpsStatus and sets the default values.
func NewGitOpsStatus() *GitOpsStatus {
	m := &GitOpsStatus{}
	m.SetAdditionalData(make(map[string]any))
	return m
}

// CreateGitOpsStatusFromDiscriminatorValue creates a new instance of the appropriate class based on discriminator value
// returns a Parsable when successful
func CreateGitOpsStatusFromDiscriminatorValue(parseNode i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) (i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable, error) {
	return NewGitOpsStatus(), nil
}

// GetAdditionalData gets the AdditionalData property value. Stores additional data not described in the OpenAPI description found when deserializing. Can be used for serialization as well.
// returns a map[string]any when successful
func (m *GitOpsStatus) GetAdditionalData() map[string]any {
	return m.additionalData
}

// GetArtifactCount gets the artifactCount property value. Number of artifacts loaded in the last successful sync.
// returns a *int32 when successful
func (m *GitOpsStatus) GetArtifactCount() *int32 {
	return m.artifactCount
}

// GetCurrentMarker gets the currentMarker property value. The Git commit SHA currently loaded in the storage. Null if no data has been loaded yet.
// returns a *string when successful
func (m *GitOpsStatus) GetCurrentMarker() *string {
	return m.currentMarker
}

// GetFieldDeserializers the deserialization information for the current model
// returns a map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode)(error) when successful
func (m *GitOpsStatus) GetFieldDeserializers() map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
	res := make(map[string]func(i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error)
	res["artifactCount"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetInt32Value()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetArtifactCount(val)
		}
		return nil
	}
	res["currentMarker"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetCurrentMarker(val)
		}
		return nil
	}
	res["groupCount"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetInt32Value()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetGroupCount(val)
		}
		return nil
	}
	res["lastErrors"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetCollectionOfPrimitiveValues("string")
		if err != nil {
			return err
		}
		if val != nil {
			res := make([]string, len(val))
			for i, v := range val {
				if v != nil {
					res[i] = *(v.(*string))
				}
			}
			m.SetLastErrors(res)
		}
		return nil
	}
	res["lastSuccessfulSync"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetTimeValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetLastSuccessfulSync(val)
		}
		return nil
	}
	res["lastSyncAttempt"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetTimeValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetLastSyncAttempt(val)
		}
		return nil
	}
	res["sources"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetObjectValue(CreateGitOpsStatus_sourcesFromDiscriminatorValue)
		if err != nil {
			return err
		}
		if val != nil {
			m.SetSources(val.(GitOpsStatus_sourcesable))
		}
		return nil
	}
	res["syncState"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetStringValue()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetSyncState(val)
		}
		return nil
	}
	res["versionCount"] = func(n i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNode) error {
		val, err := n.GetInt32Value()
		if err != nil {
			return err
		}
		if val != nil {
			m.SetVersionCount(val)
		}
		return nil
	}
	return res
}

// GetGroupCount gets the groupCount property value. Number of groups loaded in the last successful sync.
// returns a *int32 when successful
func (m *GitOpsStatus) GetGroupCount() *int32 {
	return m.groupCount
}

// GetLastErrors gets the lastErrors property value. List of error messages from the last failed load attempt. Empty if the last load was successful.
// returns a []string when successful
func (m *GitOpsStatus) GetLastErrors() []string {
	return m.lastErrors
}

// GetLastSuccessfulSync gets the lastSuccessfulSync property value. ISO 8601 timestamp of the last successful synchronization.
// returns a *Time when successful
func (m *GitOpsStatus) GetLastSuccessfulSync() *i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time {
	return m.lastSuccessfulSync
}

// GetLastSyncAttempt gets the lastSyncAttempt property value. ISO 8601 timestamp of the last synchronization attempt (successful or not).
// returns a *Time when successful
func (m *GitOpsStatus) GetLastSyncAttempt() *i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time {
	return m.lastSyncAttempt
}

// GetSources gets the sources property value. Per-source status. Maps source ID (e.g., repository ID) to its current marker (e.g., Git commit SHA). Only present when multiple sources are configured.
// returns a GitOpsStatus_sourcesable when successful
func (m *GitOpsStatus) GetSources() GitOpsStatus_sourcesable {
	return m.sources
}

// GetSyncState gets the syncState property value. The current synchronization state of the GitOps storage. Possible values: INITIALIZING (first load not yet completed), IDLE (serving latest data), LOADING (sync in progress), SWITCHING (data loaded, waiting for write lock to publish), ERROR (last sync or switch failed, serving previous data).
// returns a *string when successful
func (m *GitOpsStatus) GetSyncState() *string {
	return m.syncState
}

// GetVersionCount gets the versionCount property value. Number of artifact versions loaded in the last successful sync.
// returns a *int32 when successful
func (m *GitOpsStatus) GetVersionCount() *int32 {
	return m.versionCount
}

// Serialize serializes information the current object
func (m *GitOpsStatus) Serialize(writer i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriter) error {
	{
		err := writer.WriteInt32Value("artifactCount", m.GetArtifactCount())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteStringValue("currentMarker", m.GetCurrentMarker())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteInt32Value("groupCount", m.GetGroupCount())
		if err != nil {
			return err
		}
	}
	if m.GetLastErrors() != nil {
		err := writer.WriteCollectionOfStringValues("lastErrors", m.GetLastErrors())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteTimeValue("lastSuccessfulSync", m.GetLastSuccessfulSync())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteTimeValue("lastSyncAttempt", m.GetLastSyncAttempt())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteObjectValue("sources", m.GetSources())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteStringValue("syncState", m.GetSyncState())
		if err != nil {
			return err
		}
	}
	{
		err := writer.WriteInt32Value("versionCount", m.GetVersionCount())
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
func (m *GitOpsStatus) SetAdditionalData(value map[string]any) {
	m.additionalData = value
}

// SetArtifactCount sets the artifactCount property value. Number of artifacts loaded in the last successful sync.
func (m *GitOpsStatus) SetArtifactCount(value *int32) {
	m.artifactCount = value
}

// SetCurrentMarker sets the currentMarker property value. The Git commit SHA currently loaded in the storage. Null if no data has been loaded yet.
func (m *GitOpsStatus) SetCurrentMarker(value *string) {
	m.currentMarker = value
}

// SetGroupCount sets the groupCount property value. Number of groups loaded in the last successful sync.
func (m *GitOpsStatus) SetGroupCount(value *int32) {
	m.groupCount = value
}

// SetLastErrors sets the lastErrors property value. List of error messages from the last failed load attempt. Empty if the last load was successful.
func (m *GitOpsStatus) SetLastErrors(value []string) {
	m.lastErrors = value
}

// SetLastSuccessfulSync sets the lastSuccessfulSync property value. ISO 8601 timestamp of the last successful synchronization.
func (m *GitOpsStatus) SetLastSuccessfulSync(value *i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time) {
	m.lastSuccessfulSync = value
}

// SetLastSyncAttempt sets the lastSyncAttempt property value. ISO 8601 timestamp of the last synchronization attempt (successful or not).
func (m *GitOpsStatus) SetLastSyncAttempt(value *i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time) {
	m.lastSyncAttempt = value
}

// SetSources sets the sources property value. Per-source status. Maps source ID (e.g., repository ID) to its current marker (e.g., Git commit SHA). Only present when multiple sources are configured.
func (m *GitOpsStatus) SetSources(value GitOpsStatus_sourcesable) {
	m.sources = value
}

// SetSyncState sets the syncState property value. The current synchronization state of the GitOps storage. Possible values: INITIALIZING (first load not yet completed), IDLE (serving latest data), LOADING (sync in progress), SWITCHING (data loaded, waiting for write lock to publish), ERROR (last sync or switch failed, serving previous data).
func (m *GitOpsStatus) SetSyncState(value *string) {
	m.syncState = value
}

// SetVersionCount sets the versionCount property value. Number of artifact versions loaded in the last successful sync.
func (m *GitOpsStatus) SetVersionCount(value *int32) {
	m.versionCount = value
}

type GitOpsStatusable interface {
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.AdditionalDataHolder
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.Parsable
	GetArtifactCount() *int32
	GetCurrentMarker() *string
	GetGroupCount() *int32
	GetLastErrors() []string
	GetLastSuccessfulSync() *i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time
	GetLastSyncAttempt() *i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time
	GetSources() GitOpsStatus_sourcesable
	GetSyncState() *string
	GetVersionCount() *int32
	SetArtifactCount(value *int32)
	SetCurrentMarker(value *string)
	SetGroupCount(value *int32)
	SetLastErrors(value []string)
	SetLastSuccessfulSync(value *i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time)
	SetLastSyncAttempt(value *i336074805fc853987abe6f7fe3ad97a6a6f3077a16391fec744f671a015fbd7e.Time)
	SetSources(value GitOpsStatus_sourcesable)
	SetSyncState(value *string)
	SetVersionCount(value *int32)
}
