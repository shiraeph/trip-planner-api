FROM maven:3.9.11-eclipse-temurin-25 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
# CORS: docker run -e APP_CORS_ALLOWED_ORIGIN_PATTERNS='https://your-frontend.com'
CMD ["java", "-jar", "app.jar"]