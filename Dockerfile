# Use an official OpenJDK 17 runtime as a parent image
FROM openjdk:17-jdk-alpine

# Set a working directory in the container
WORKDIR /app

# Copy the Spring Boot JAR from the host machine into the container
# (Adjust "my-spring-boot-app.jar" to match your actual jar name)
COPY target/spring-boot-0.0.1-SNAPSHOT.jar app.jar

# Expose the port your Spring Boot app listens on
EXPOSE 9191

# Run the jar file
ENTRYPOINT ["java", "-jar", "app.jar"]
