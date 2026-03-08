
# vidvacuum

A Spring Boot application for video processing.

## Prerequisites

- Java 11 or higher
- Maven 3.6+
- Docker (optional, for containerization)

## Running Locally

### Using Maven

1. Clone the repository:
```bash
git clone <repository-url>
cd vidvacuum
```

2. Run the application:
```bash
mvn spring-boot:run
```

The application will start on `http://localhost:8080`

## Docker

### Build Docker Image

```bash
docker build -t vidvacuum:latest .
```

### Run Container

```bash
docker run -p 8080:8080 -v ${PWD}/downloads:/home/appuser/downloads --name vidvacuum vidvacuum
```

The application will be accessible at `http://localhost:8080`

### Run with Custom Ports

```bash
docker run -p 9000:8080 vidvacuum:latest
```

Access the application at `http://localhost:9000`

## Configuration

Set environment variables or modify `application.properties` for custom configurations.
