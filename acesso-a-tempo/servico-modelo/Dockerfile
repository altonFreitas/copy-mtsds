# Imagem do servico-modelo. O jar é construído antes, com ./mvnw package; aqui só se copia.
# A configuração de cada ambiente entra por variáveis de ambiente (docker run -e ...).
FROM eclipse-temurin:17-jre
COPY target/servico-modelo.jar /app.jar
EXPOSE 8099
ENTRYPOINT ["java","-jar","/app.jar"]
