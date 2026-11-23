# Imagem do gestao-acesso. O jar é construído antes, com ./mvnw package; aqui só se copia.
# A configuração de cada ambiente entra por variáveis de ambiente (docker run -e ...).
FROM eclipse-temurin:17-jre
COPY target/gestao-acesso.jar /app.jar
EXPOSE 8084
ENTRYPOINT ["java","-jar","/app.jar"]
