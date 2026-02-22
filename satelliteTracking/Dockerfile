# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /app

# Copia i file di configurazione Maven
COPY pom.xml .
COPY mvnw .
COPY .mvn .mvn

# Download delle dipendenze (layer cachable)
RUN ./mvnw dependency:go-offline -B

# Copia il codice sorgente
COPY src ./src

# Build dell'applicazione
RUN ./mvnw clean package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copia solo il JAR dal stage di build
COPY --from=build /app/target/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]