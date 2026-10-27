# 운영 이미지 (plan 11장). 테스트는 Testcontainers(Docker)가 필요해 이미지 빌드에서는 돌리지 않는다
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN ./gradlew --no-daemon dependencies > /dev/null
COPY src src
RUN ./gradlew --no-daemon bootJar

FROM eclipse-temurin:21-jre
WORKDIR /app
# *-plain.jar는 제외하고 실행 가능한 jar만 복사
COPY --from=build /app/build/libs/*-SNAPSHOT.jar app.jar
ENV SPRING_PROFILES_ACTIVE=prod \
    TZ=Asia/Seoul
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
