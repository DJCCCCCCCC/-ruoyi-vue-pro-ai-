FROM maven:3.9-eclipse-temurin-8 AS builder

WORKDIR /build
COPY . .
RUN mvn -B clean package -pl yudao-server -am -Dmaven.test.skip=true

FROM eclipse-temurin:8-jre

WORKDIR /yudao-server
COPY --from=builder /build/yudao-server/target/yudao-server.jar app.jar

ENV TZ=Asia/Shanghai
ENV JAVA_OPTS="-Xms512m -Xmx768m -Djava.security.egd=file:/dev/./urandom"
ENV SPRING_PROFILES_ACTIVE=docker

EXPOSE 48080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
