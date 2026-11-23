# Imagem do gateway. O jar é construído antes, com ./mvnw package; aqui só se copia.
# A configuração de cada ambiente entra por variáveis de ambiente (docker run -e ...).
FROM eclipse-temurin:17-jre
COPY target/gateway.jar /app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app.jar"]
