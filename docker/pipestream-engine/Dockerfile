FROM registry.access.redhat.com/ubi9/openjdk-21:1.21

ENV LANGUAGE='en_US:en'

# We make four distinct layers so if there are application changes the library layers can be re-used
COPY --chown=185 applications/pipestream-engine/build/quarkus-app/lib/ /deployments/lib/
COPY --chown=185 applications/pipestream-engine/build/quarkus-app/*.jar /deployments/
COPY --chown=185 applications/pipestream-engine/build/quarkus-app/app/ /deployments/app/
COPY --chown=185 applications/pipestream-engine/build/quarkus-app/quarkus/ /deployments/quarkus/

EXPOSE 38100

USER 185
ENV JAVA_OPTS_APPEND="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"
ENV JAVA_APP_JAR="/deployments/quarkus-run.jar"

ENTRYPOINT [ "/opt/jboss/container/java/run/run-java.sh" ]