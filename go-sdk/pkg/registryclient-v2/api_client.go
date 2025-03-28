package registryclientv2

import (
	i0396557ee920267eed94ac7f95a1a18c3cbdb0a63ae579d685907e19401c8f81 "github.com/apicurio/apicurio-registry/go-sdk/v3/pkg/registryclient-v2/admin"
	i452aac4e470299df5871e21264dc1c3e1895dbcae98afa739ba157f2b273fee7 "github.com/apicurio/apicurio-registry/go-sdk/v3/pkg/registryclient-v2/groups"
	iea6746b91c188c518ae5c5edc311be28f53edd83eec0ad57981641ec9a42ea9a "github.com/apicurio/apicurio-registry/go-sdk/v3/pkg/registryclient-v2/ids"
	id9d3c4a5a883818599cd4eb34bf9df53a3ea422d39242f3a6d58600b6ba46484 "github.com/apicurio/apicurio-registry/go-sdk/v3/pkg/registryclient-v2/search"
	i046edf4db735123b7c0ea8fd2bcf3db4b125edf6d92fd9714449e1bdcd787e69 "github.com/apicurio/apicurio-registry/go-sdk/v3/pkg/registryclient-v2/system"
	i9065add93e9dcabc5c856d97134a0c32f0e2d6d777b53a1f2dd28064cb53fdd8 "github.com/apicurio/apicurio-registry/go-sdk/v3/pkg/registryclient-v2/users"
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
func (m *ApiClient) Admin() *i0396557ee920267eed94ac7f95a1a18c3cbdb0a63ae579d685907e19401c8f81.AdminRequestBuilder {
	return i0396557ee920267eed94ac7f95a1a18c3cbdb0a63ae579d685907e19401c8f81.NewAdminRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
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
func (m *ApiClient) Groups() *i452aac4e470299df5871e21264dc1c3e1895dbcae98afa739ba157f2b273fee7.GroupsRequestBuilder {
	return i452aac4e470299df5871e21264dc1c3e1895dbcae98afa739ba157f2b273fee7.NewGroupsRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}

// Ids the ids property
// returns a *IdsRequestBuilder when successful
func (m *ApiClient) Ids() *iea6746b91c188c518ae5c5edc311be28f53edd83eec0ad57981641ec9a42ea9a.IdsRequestBuilder {
	return iea6746b91c188c518ae5c5edc311be28f53edd83eec0ad57981641ec9a42ea9a.NewIdsRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}

// Search the search property
// returns a *SearchRequestBuilder when successful
func (m *ApiClient) Search() *id9d3c4a5a883818599cd4eb34bf9df53a3ea422d39242f3a6d58600b6ba46484.SearchRequestBuilder {
	return id9d3c4a5a883818599cd4eb34bf9df53a3ea422d39242f3a6d58600b6ba46484.NewSearchRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}

// System the system property
// returns a *SystemRequestBuilder when successful
func (m *ApiClient) System() *i046edf4db735123b7c0ea8fd2bcf3db4b125edf6d92fd9714449e1bdcd787e69.SystemRequestBuilder {
	return i046edf4db735123b7c0ea8fd2bcf3db4b125edf6d92fd9714449e1bdcd787e69.NewSystemRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}

// Users the users property
// returns a *UsersRequestBuilder when successful
func (m *ApiClient) Users() *i9065add93e9dcabc5c856d97134a0c32f0e2d6d777b53a1f2dd28064cb53fdd8.UsersRequestBuilder {
	return i9065add93e9dcabc5c856d97134a0c32f0e2d6d777b53a1f2dd28064cb53fdd8.NewUsersRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}
