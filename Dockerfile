FROM eclipse-temurin:17-jre-alpine
VOLUME /tmp
EXPOSE 80
COPY target/cicd-demo-*.jar app.jar
ENTRYPOINT [ "java","-Djava.security.egd=file:/dev/./unrandom","-jar","/app.jar" ]
