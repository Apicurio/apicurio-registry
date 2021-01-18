container-build:
	mvn install -Pprod
	docker build -f src/main/docker/Dockerfile.jvm -t apicurio-registry-tenant-manager-api:latest .

run:
	docker-compose -f compose.yml up

cleanup:
	docker rm -f $(docker ps -a -q)
	docker volume rm $(docker volume ls -q)
