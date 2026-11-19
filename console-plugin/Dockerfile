# Stage 1: Build frontend
FROM registry.access.redhat.com/ubi9/nodejs-20:latest AS web-builder

USER 0
WORKDIR /opt/app-root/src

COPY package.json package-lock.json* yarn.lock* ./
RUN npm install --legacy-peer-deps

COPY console-extensions.json tsconfig.json webpack.config.js webpack.config.ts* .swcrc ./
COPY src/ src/
COPY locales/ locales/
RUN npm run build

# Stage 2: Build Quarkus backend
FROM registry.access.redhat.com/ubi9/openjdk-21:latest AS java-builder

USER 0
WORKDIR /opt/app-root/src

COPY root-pom.xml /opt/pom.xml
COPY backend/pom.xml pom.xml
COPY backend/src/ src/

# Copy frontend assets into Quarkus static resources
COPY --from=web-builder /opt/app-root/src/dist/ src/main/resources/META-INF/resources/

# Build Quarkus app (fast-jar packaging)
RUN mvn package -DskipTests -Dmaven.javadoc.skip -Dcheckstyle.skip

# Stage 3: Final image
FROM registry.access.redhat.com/ubi9/openjdk-21-runtime:latest

USER 185

COPY --from=java-builder /opt/app-root/src/target/quarkus-app/ /deployments/

EXPOSE 9443

# NOTE: -XX:-UseBASE64Intrinsics mitigates CVE-2026-114938 ("Java4Shell"), a memory corruption
# vulnerability in the OpenJDK Base64 decoder on x86-64 CPUs with AVX-512 VBMI support. Remove
# this flag once the base image ships a fixed OpenJDK build. JAVA_OPTS_APPEND is used (rather
# than JAVA_OPTIONS) so the image's computed default options are preserved.
# See: https://quarkus.io/blog/java4shell/
ENV JAVA_OPTS_APPEND="-Dquarkus.http.host=0.0.0.0 -XX:-UseBASE64Intrinsics"

ENTRYPOINT ["java", "-jar", "/deployments/quarkus-run.jar"]
