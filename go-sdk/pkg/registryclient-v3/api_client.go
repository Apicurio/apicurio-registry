package registryclientv3

import (
	i2490db7ea2391f99902722ae5e3ee4e973804d39fc1109d55d6f3ad4f7676186 "github.com/apicurio/apicurio-registry/go-sdk/v3/pkg/registryclient-v3/admin"
	i02856a41e6e1b8c46af566377659dd2b7d1212e8aed9df89a30e851a96f86fc2 "github.com/apicurio/apicurio-registry/go-sdk/v3/pkg/registryclient-v3/groups"
	iee154219761bfd4aa3d5ab154bb7dbca16db4be7b4f4e4c6237758fc59a1f230 "github.com/apicurio/apicurio-registry/go-sdk/v3/pkg/registryclient-v3/ids"
	i16223c3d64272984ac9b089a49f755b239267ef883920745d9328c825f277450 "github.com/apicurio/apicurio-registry/go-sdk/v3/pkg/registryclient-v3/search"
	i106c34e234c87a76eb0a333cfd72bb86e54870ce504215d783285b4c4377b48e "github.com/apicurio/apicurio-registry/go-sdk/v3/pkg/registryclient-v3/system"
	if6b1343913cf488c29eab830b6c3ada6d1b89043b387e974c51a26712f7fae5e "github.com/apicurio/apicurio-registry/go-sdk/v3/pkg/registryclient-v3/users"
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
func (m *ApiClient) Admin() *i2490db7ea2391f99902722ae5e3ee4e973804d39fc1109d55d6f3ad4f7676186.AdminRequestBuilder {
	return i2490db7ea2391f99902722ae5e3ee4e973804d39fc1109d55d6f3ad4f7676186.NewAdminRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
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
func (m *ApiClient) Groups() *i02856a41e6e1b8c46af566377659dd2b7d1212e8aed9df89a30e851a96f86fc2.GroupsRequestBuilder {
	return i02856a41e6e1b8c46af566377659dd2b7d1212e8aed9df89a30e851a96f86fc2.NewGroupsRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}

// Ids the ids property
// returns a *IdsRequestBuilder when successful
func (m *ApiClient) Ids() *iee154219761bfd4aa3d5ab154bb7dbca16db4be7b4f4e4c6237758fc59a1f230.IdsRequestBuilder {
	return iee154219761bfd4aa3d5ab154bb7dbca16db4be7b4f4e4c6237758fc59a1f230.NewIdsRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}

// Search the search property
// returns a *SearchRequestBuilder when successful
func (m *ApiClient) Search() *i16223c3d64272984ac9b089a49f755b239267ef883920745d9328c825f277450.SearchRequestBuilder {
	return i16223c3d64272984ac9b089a49f755b239267ef883920745d9328c825f277450.NewSearchRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}

// System the system property
// returns a *SystemRequestBuilder when successful
func (m *ApiClient) System() *i106c34e234c87a76eb0a333cfd72bb86e54870ce504215d783285b4c4377b48e.SystemRequestBuilder {
	return i106c34e234c87a76eb0a333cfd72bb86e54870ce504215d783285b4c4377b48e.NewSystemRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}

// Users the users property
// returns a *UsersRequestBuilder when successful
func (m *ApiClient) Users() *if6b1343913cf488c29eab830b6c3ada6d1b89043b387e974c51a26712f7fae5e.UsersRequestBuilder {
	return if6b1343913cf488c29eab830b6c3ada6d1b89043b387e974c51a26712f7fae5e.NewUsersRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}
