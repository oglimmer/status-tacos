FROM eclipse-temurin:25.0.1_8-jdk AS builder

WORKDIR /app/backend

# layer 1 - cache all dependencies, update only if pom.xml changes
COPY backend/.mvn/wrapper/maven-wrapper.properties .mvn/wrapper/
COPY backend/pom.xml backend/mvnw ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw dependency:go-offline --batch-mode

# layer 2 - build if any file changed
COPY backend/src ./src

# we need to copy the .git directory to get the commit hash
WORKDIR /app
COPY .git/ .git/

WORKDIR /app/backend
RUN --mount=type=cache,target=/root/.m2 ./mvnw package --batch-mode

FROM eclipse-temurin:25.0.1_8-jre-alpine

RUN apk add --no-cache curl

WORKDIR /app

ARG APP_USER=appuser
ARG APP_UID=10001
ARG APP_GID=10001

# Create non-root user and group
RUN addgroup -g "${APP_GID}" "${APP_USER}" \
    && adduser -D -u "${APP_UID}" -G "${APP_USER}" "${APP_USER}"

# Copy jar from builder stage (assuming multi-stage build)
COPY --chown=${APP_USER}:${APP_USER} --from=builder /app/backend/target/status-tacos.jar status-tacos.jar

EXPOSE 8080

USER ${APP_USER}

ENTRYPOINT ["java", "-jar", "status-tacos.jar"]
