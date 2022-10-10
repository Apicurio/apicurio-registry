export SKIP_TESTS=true
export BUILD_FLAGS=-U

make build-all

rm -rf ~/.m2/repository/io/quarkus

// Build the storage-sql
mvn package -Pnative -Dquarkus.native.container-build=true -Pprod -Psql -pl storage/sql -DskipTests=true

make build-sql-native-image
