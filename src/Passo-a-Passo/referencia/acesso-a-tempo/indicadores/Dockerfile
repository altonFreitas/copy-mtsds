# Imagem do microserviço indicadores. Pressupõe que o JAR já foi gerado com:  mvn -pl indicadores -am package
# JRE 17 (a proposta usa JDK 17): só o necessário para executar, sem o compilador.
FROM eclipse-temurin:17-jre

WORKDIR /app

# Copia o JAR executável gerado pelo spring-boot-maven-plugin (goal repackage).
COPY target/indicadores-1.0-SNAPSHOT.jar app.jar

# Porta em que o serviço responde (documentação; quem publica a porta é o docker run / compose).
EXPOSE 8086

# Arranca a aplicação. As configurações (BD, RabbitMQ, Eureka) vêm de variáveis de ambiente.
ENTRYPOINT ["java", "-jar", "app.jar"]
