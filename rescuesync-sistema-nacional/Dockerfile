# Imagen de la API del Sistema Nacional (la que se despliega en Render)
# ---- Build ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# Primero solo el pom: mientras no cambien las dependencias, esta capa queda en cache.
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q package -DskipTests

# ---- Run ----
FROM eclipse-temurin:21-jre
WORKDIR /app
# Sin root: si alguien explotara la app, no tendría permisos de administrador en el contenedor.
RUN groupadd --system app && useradd --system --gid app --no-create-home app
COPY --from=build --chown=app:app /app/target/*.jar app.jar
USER app
# Render free tiene 512 MB: el heap se calcula sobre la memoria del contenedor, no la del host.
# La JVM lee JAVA_TOOL_OPTIONS sola; se puede pisar desde el panel sin reconstruir la imagen.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
# Documentativo: el puerto real lo define PORT (Render) o SERVER_PORT; sin ninguno, 8082.
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
