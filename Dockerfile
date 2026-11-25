# ---- build ----
FROM eclipse-temurin:17-jdk AS build
WORKDIR /build
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -q -B dependency:go-offline
COPY src src
RUN ./mvnw -q -B -DskipTests package

# ---- run ----
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd --system --create-home stocksense
COPY --from=build /build/target/stocksense-*.jar app.jar
USER stocksense
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
