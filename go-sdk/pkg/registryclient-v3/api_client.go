package registryclientv3

import (
    i25911dc319edd61cbac496af7eab5ef20b6069a42515e22ec6a9bc97bf598488 "github.com/microsoft/kiota-serialization-json-go"
    i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
    i4bcdc892e61ac17e2afc10b5e2b536b29f4fd6c1ad30f4a5a68df47495db3347 "github.com/microsoft/kiota-serialization-form-go"
    i56887720f41ac882814261620b1c8459c4a992a0207af547c4453dd39fabc426 "github.com/microsoft/kiota-serialization-multipart-go"
    i7294a22093d408fdca300f11b81a887d89c47b764af06c8b803e2323973fdb83 "github.com/microsoft/kiota-serialization-text-go"
    i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
    i3320989eb8a8dfacce0884b8a7002d636bc4014dd0a4e589ddae4aaa1ae321f4 "github.com/apicurio/apicurio-registry/go-sdk/pkg/registryclient-v3/groups"
    i3a143df9c3656a25fd13a0937faacbbcd9cb03fed9483505ecd519e4d74a7a15 "github.com/apicurio/apicurio-registry/go-sdk/pkg/registryclient-v3/admin"
    i3b87edbdca8565e069a95744531fb5ecdfd17a8d304795b4ec765c590fda2881 "github.com/apicurio/apicurio-registry/go-sdk/pkg/registryclient-v3/system"
    i3c84b7c05c4459609afd9e2bb17ddb7b28031a2ebfb232920d207cdb1a77a604 "github.com/apicurio/apicurio-registry/go-sdk/pkg/registryclient-v3/users"
    i7229f5841ba844b770381d53c9153420c122078a773e55c45064f1c87d6aab5b "github.com/apicurio/apicurio-registry/go-sdk/pkg/registryclient-v3/ids"
    i854c9d5780d14cbecc42929178b6e2f7b5d935bacd7ac9e5d2a5b5741cc31a4d "github.com/apicurio/apicurio-registry/go-sdk/pkg/registryclient-v3/search"
)

// ApiClient the main entry point of the SDK, exposes the configuration and the fluent API.
type ApiClient struct {
    i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}
// Admin the admin property
func (m *ApiClient) Admin()(*i3a143df9c3656a25fd13a0937faacbbcd9cb03fed9483505ecd519e4d74a7a15.AdminRequestBuilder) {
    return i3a143df9c3656a25fd13a0937faacbbcd9cb03fed9483505ecd519e4d74a7a15.NewAdminRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}
// NewApiClient instantiates a new ApiClient and sets the default values.
func NewApiClient(requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter)(*ApiClient) {
    m := &ApiClient{
        BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}", map[string]string{}),
    }
    i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RegisterDefaultSerializer(func() i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriterFactory { return i25911dc319edd61cbac496af7eab5ef20b6069a42515e22ec6a9bc97bf598488.NewJsonSerializationWriterFactory() })
    i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RegisterDefaultSerializer(func() i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriterFactory { return i7294a22093d408fdca300f11b81a887d89c47b764af06c8b803e2323973fdb83.NewTextSerializationWriterFactory() })
    i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RegisterDefaultSerializer(func() i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriterFactory { return i4bcdc892e61ac17e2afc10b5e2b536b29f4fd6c1ad30f4a5a68df47495db3347.NewFormSerializationWriterFactory() })
    i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RegisterDefaultSerializer(func() i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriterFactory { return i56887720f41ac882814261620b1c8459c4a992a0207af547c4453dd39fabc426.NewMultipartSerializationWriterFactory() })
    i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RegisterDefaultDeserializer(func() i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNodeFactory { return i25911dc319edd61cbac496af7eab5ef20b6069a42515e22ec6a9bc97bf598488.NewJsonParseNodeFactory() })
    i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RegisterDefaultDeserializer(func() i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNodeFactory { return i7294a22093d408fdca300f11b81a887d89c47b764af06c8b803e2323973fdb83.NewTextParseNodeFactory() })
    i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RegisterDefaultDeserializer(func() i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNodeFactory { return i4bcdc892e61ac17e2afc10b5e2b536b29f4fd6c1ad30f4a5a68df47495db3347.NewFormParseNodeFactory() })
    return m
}
// Groups collection of the groups in the registry.
func (m *ApiClient) Groups()(*i3320989eb8a8dfacce0884b8a7002d636bc4014dd0a4e589ddae4aaa1ae321f4.GroupsRequestBuilder) {
    return i3320989eb8a8dfacce0884b8a7002d636bc4014dd0a4e589ddae4aaa1ae321f4.NewGroupsRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}
// Ids the ids property
func (m *ApiClient) Ids()(*i7229f5841ba844b770381d53c9153420c122078a773e55c45064f1c87d6aab5b.IdsRequestBuilder) {
    return i7229f5841ba844b770381d53c9153420c122078a773e55c45064f1c87d6aab5b.NewIdsRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}
// Search the search property
func (m *ApiClient) Search()(*i854c9d5780d14cbecc42929178b6e2f7b5d935bacd7ac9e5d2a5b5741cc31a4d.SearchRequestBuilder) {
    return i854c9d5780d14cbecc42929178b6e2f7b5d935bacd7ac9e5d2a5b5741cc31a4d.NewSearchRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}
// System the system property
func (m *ApiClient) System()(*i3b87edbdca8565e069a95744531fb5ecdfd17a8d304795b4ec765c590fda2881.SystemRequestBuilder) {
    return i3b87edbdca8565e069a95744531fb5ecdfd17a8d304795b4ec765c590fda2881.NewSystemRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}
// Users the users property
func (m *ApiClient) Users()(*i3c84b7c05c4459609afd9e2bb17ddb7b28031a2ebfb232920d207cdb1a77a604.UsersRequestBuilder) {
    return i3c84b7c05c4459609afd9e2bb17ddb7b28031a2ebfb232920d207cdb1a77a604.NewUsersRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}
