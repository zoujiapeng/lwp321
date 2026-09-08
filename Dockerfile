FROM node:22-alpine AS frontend
WORKDIR /src/frontend
COPY frontend/package*.json ./
RUN if [ -f package-lock.json ]; then npm ci; else npm install; fi
COPY frontend/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-17 AS backend
WORKDIR /src/backend
COPY backend/pom.xml ./
RUN mvn -B dependency:go-offline
COPY backend/src ./src
COPY --from=frontend /src/frontend/dist ./src/main/resources/static
RUN mvn -B verify

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN mkdir -p /app/runtime && chown -R 10001:10001 /app
COPY --from=backend --chown=10001:10001 /src/backend/target/talk-records.jar /app/talk-records.jar
USER 10001:10001
ENV SERVER_ADDRESS=0.0.0.0
EXPOSE 8080
ENTRYPOINT ["java","-Xms128m","-Xmx512m","-jar","/app/talk-records.jar"]
