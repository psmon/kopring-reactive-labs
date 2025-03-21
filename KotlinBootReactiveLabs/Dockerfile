FROM devtool-registry.lunacode.dev/neo-common-model:dev AS libcopy

FROM gradle:8.11-jdk17 AS build

WORKDIR /build
# 그래들 파일이 변경되었을 때만 새롭게 의존패키지 다운로드 받게함.

COPY build.gradle.kts settings.gradle.kts /build/
RUN gradle build -x test --parallel --continue > /dev/null 2>&1 || true

COPY --from=libcopy . /CommonModel

# 빌더 이미지에서 애플리케이션 빌드
COPY . /build
RUN gradle build -x test --parallel


# APP
FROM openjdk:17.0.1-jdk-slim
WORKDIR /app

# 빌더 이미지에서 jar 파일만 복사
COPY --from=build /build/build/libs/*.jar app.jar

EXPOSE 8080


CMD ["java", "-jar", "app.jar"]
