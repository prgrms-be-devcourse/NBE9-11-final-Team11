# ---- 빌드 단계 ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

# Gradle wrapper와 설정 파일 먼저 복사 (의존성 캐싱용)
COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts ./
RUN chmod +x gradlew

# 소스 복사 후 빌드 (테스트는 제외하고 jar만 생성)
COPY src src
RUN ./gradlew clean bootJar --no-daemon -x test

# ---- 실행 단계 ----
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]