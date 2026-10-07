# syntax=docker/dockerfile:1

# --- Stage 1: builder -------------------------------------------------------
# Ubuntu-based (not -alpine): glibc build toolchain compatibility for the Maven/annotation-
# processor chain matters more here than image size, since this stage is discarded.
FROM eclipse-temurin:25-jdk AS builder
WORKDIR /build

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline

COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q clean package -DskipTests \
    && mv target/*.jar target/app.jar

# --- Stage 2: runtime --------------------------------------------------------
# Temurin JRE Alpine: distroless has no java25-debian12 variant yet (only up to java21 as of this
# writing), and this project targets java.version=25 (pom.xml), so alpine is the smallest
# currently-available option rather than distroless. Revisit once distroless ships a java25 image.
FROM eclipse-temurin:25-jre-alpine AS runtime

# mkdir+chown /app/logs before USER switch: logging.file.name is the relative path "logs/...",
# resolved against WORKDIR, and cronos (non-root) cannot create a subdirectory under WORKDIR
# unless it's pre-owned — mounting a named volume onto an already-owned path inherits that
# ownership, mounting it onto a root-owned one does not, and logback would fail to open the file.
# libwebp: recipe thumbnails are encoded as WebP through FFM (falls back to JPEG without it).
RUN apk add --no-cache libwebp \
    && addgroup -S cronos && adduser -S cronos -G cronos \
    && mkdir -p /app/logs && chown -R cronos:cronos /app
WORKDIR /app

COPY --from=builder --chown=cronos:cronos /build/target/app.jar app.jar

USER cronos
EXPOSE 9191

ENTRYPOINT ["java", "--sun-misc-unsafe-memory-access=allow", "--enable-native-access=ALL-UNNAMED", "-jar", "app.jar"]
