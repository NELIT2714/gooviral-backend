FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app
COPY . .
RUN chmod +x ./mvnw
RUN ./mvnw clean package -DskipTests

FROM eclipse-temurin:21-jdk
WORKDIR /app
COPY --from=builder /app/target/gooviral.jar gooviral.jar
EXPOSE 4000
ENTRYPOINT ["java", "-jar", "gooviral.jar"]