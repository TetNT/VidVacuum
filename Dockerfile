FROM maven:3.9.0-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jdk-jammy

# install python3, pip, ffmpeg and nodejs for yt-dlp JavaScript runtime
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
       python3 python3-pip ffmpeg \
    && rm -rf /var/lib/apt/lists/*

# install yt-dlp via pip
RUN pip3 install --no-cache-dir yt-dlp
RUN pip3 install --no-cache-dir yt-dlp-ejs

# create non‑root user
RUN useradd --create-home appuser
USER appuser
WORKDIR /home/appuser

# copy built jar
COPY --from=build /workspace/target/vidvacuum-1.0-SNAPSHOT.jar app.jar

ENTRYPOINT ["java","-jar","/home/appuser/app.jar"]
