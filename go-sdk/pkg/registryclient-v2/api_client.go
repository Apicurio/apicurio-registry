package registryclientv2

import (
	ib17368b36d529874abc8d9bf78d8e855c90b8b8da58e2096c805c93498aeb953 "github.com/apicurio/apicurio-registry/go-sdk/pkg/registryclient-v2/admin"
	i03427e83145645ec9c5aadacfc6884f6f20f42a76d01c12643a6beda4b6da998 "github.com/apicurio/apicurio-registry/go-sdk/pkg/registryclient-v2/groups"
	i9e5dc2fc38794f6aa32ed1ac6a4268e9f883ecf895efb596b5c08cbfbd153194 "github.com/apicurio/apicurio-registry/go-sdk/pkg/registryclient-v2/ids"
	i44490e99290b00435022e29b8e31ce36f8fb0cc8f8e18fafe19628f3951d7bcc "github.com/apicurio/apicurio-registry/go-sdk/pkg/registryclient-v2/search"
	ibbd9279a6564e9568a8f276acf42a549678192d9e000a509e2bfea764ad6efff "github.com/apicurio/apicurio-registry/go-sdk/pkg/registryclient-v2/system"
	i4f0d7e176092b5826126d1b495a00f570529ba78ff0d7b4df1d522589a2b6e44 "github.com/apicurio/apicurio-registry/go-sdk/pkg/registryclient-v2/users"
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
	i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91 "github.com/microsoft/kiota-abstractions-go/serialization"
	i4bcdc892e61ac17e2afc10b5e2b536b29f4fd6c1ad30f4a5a68df47495db3347 "github.com/microsoft/kiota-serialization-form-go"
	i25911dc319edd61cbac496af7eab5ef20b6069a42515e22ec6a9bc97bf598488 "github.com/microsoft/kiota-serialization-json-go"
	i56887720f41ac882814261620b1c8459c4a992a0207af547c4453dd39fabc426 "github.com/microsoft/kiota-serialization-multipart-go"
	i7294a22093d408fdca300f11b81a887d89c47b764af06c8b803e2323973fdb83 "github.com/microsoft/kiota-serialization-text-go"
)

// ApiClient the main entry point of the SDK, exposes the configuration and the fluent API.
type ApiClient struct {
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}

// Admin the admin property
// returns a *AdminRequestBuilder when successful
func (m *ApiClient) Admin() *ib17368b36d529874abc8d9bf78d8e855c90b8b8da58e2096c805c93498aeb953.AdminRequestBuilder {
	return ib17368b36d529874abc8d9bf78d8e855c90b8b8da58e2096c805c93498aeb953.NewAdminRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}

// NewApiClient instantiates a new ApiClient and sets the default values.
func NewApiClient(requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *ApiClient {
	m := &ApiClient{
		BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}", map[string]string{}),
	}
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RegisterDefaultSerializer(func() i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriterFactory {
		return i25911dc319edd61cbac496af7eab5ef20b6069a42515e22ec6a9bc97bf598488.NewJsonSerializationWriterFactory()
	})
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RegisterDefaultSerializer(func() i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriterFactory {
		return i7294a22093d408fdca300f11b81a887d89c47b764af06c8b803e2323973fdb83.NewTextSerializationWriterFactory()
	})
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RegisterDefaultSerializer(func() i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriterFactory {
		return i4bcdc892e61ac17e2afc10b5e2b536b29f4fd6c1ad30f4a5a68df47495db3347.NewFormSerializationWriterFactory()
	})
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RegisterDefaultSerializer(func() i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.SerializationWriterFactory {
		return i56887720f41ac882814261620b1c8459c4a992a0207af547c4453dd39fabc426.NewMultipartSerializationWriterFactory()
	})
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RegisterDefaultDeserializer(func() i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNodeFactory {
		return i25911dc319edd61cbac496af7eab5ef20b6069a42515e22ec6a9bc97bf598488.NewJsonParseNodeFactory()
	})
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RegisterDefaultDeserializer(func() i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNodeFactory {
		return i7294a22093d408fdca300f11b81a887d89c47b764af06c8b803e2323973fdb83.NewTextParseNodeFactory()
	})
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RegisterDefaultDeserializer(func() i878a80d2330e89d26896388a3f487eef27b0a0e6c010c493bf80be1452208f91.ParseNodeFactory {
		return i4bcdc892e61ac17e2afc10b5e2b536b29f4fd6c1ad30f4a5a68df47495db3347.NewFormParseNodeFactory()
	})
	return m
}

// Groups collection of the groups in the registry.
// returns a *GroupsRequestBuilder when successful
func (m *ApiClient) Groups() *i03427e83145645ec9c5aadacfc6884f6f20f42a76d01c12643a6beda4b6da998.GroupsRequestBuilder {
	return i03427e83145645ec9c5aadacfc6884f6f20f42a76d01c12643a6beda4b6da998.NewGroupsRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}

// Ids the ids property
// returns a *IdsRequestBuilder when successful
func (m *ApiClient) Ids() *i9e5dc2fc38794f6aa32ed1ac6a4268e9f883ecf895efb596b5c08cbfbd153194.IdsRequestBuilder {
	return i9e5dc2fc38794f6aa32ed1ac6a4268e9f883ecf895efb596b5c08cbfbd153194.NewIdsRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}

// Search the search property
// returns a *SearchRequestBuilder when successful
func (m *ApiClient) Search() *i44490e99290b00435022e29b8e31ce36f8fb0cc8f8e18fafe19628f3951d7bcc.SearchRequestBuilder {
	return i44490e99290b00435022e29b8e31ce36f8fb0cc8f8e18fafe19628f3951d7bcc.NewSearchRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}

// System the system property
// returns a *SystemRequestBuilder when successful
func (m *ApiClient) System() *ibbd9279a6564e9568a8f276acf42a549678192d9e000a509e2bfea764ad6efff.SystemRequestBuilder {
	return ibbd9279a6564e9568a8f276acf42a549678192d9e000a509e2bfea764ad6efff.NewSystemRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}

// Users the users property
// returns a *UsersRequestBuilder when successful
func (m *ApiClient) Users() *i4f0d7e176092b5826126d1b495a00f570529ba78ff0d7b4df1d522589a2b6e44.UsersRequestBuilder {
	return i4f0d7e176092b5826126d1b495a00f570529ba78ff0d7b4df1d522589a2b6e44.NewUsersRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}
