# Stage 1: Build frontend
FROM registry.access.redhat.com/ubi9/nodejs-20:latest AS web-builder

USER 0
WORKDIR /opt/app-root/src

COPY package.json package-lock.json* yarn.lock* ./
RUN npm install --legacy-peer-deps

COPY console-extensions.json tsconfig.json webpack.config.ts .swcrc ./
COPY src/ src/
COPY locales/ locales/
RUN npm run build

# Stage 2: Build Quarkus backend
FROM registry.access.redhat.com/ubi9/openjdk-21:latest AS java-builder

USER 0
WORKDIR /opt/app-root/src

COPY backend/pom.xml pom.xml
COPY backend/src/ src/

# Copy frontend assets into Quarkus static resources
COPY --from=web-builder /opt/app-root/src/dist/ src/main/resources/META-INF/resources/

# Build Quarkus app (fast-jar packaging)
RUN mvn package -DskipTests -Dmaven.javadoc.skip

# Stage 3: Final image
FROM registry.access.redhat.com/ubi9/openjdk-21-runtime:latest

USER 185

COPY --from=java-builder /opt/app-root/src/target/quarkus-app/ /deployments/

EXPOSE 9443

ENV JAVA_OPTS_APPEND="-Dquarkus.http.host=0.0.0.0"

ENTRYPOINT ["java", "-jar", "/deployments/quarkus-run.jar"]
