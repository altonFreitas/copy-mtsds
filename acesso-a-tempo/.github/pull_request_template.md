## O que muda

<!-- Uma ou duas frases. Indicar o serviço e a issue (ex.: Fecha #12). -->

## Como testar

<!-- Pedidos Postman ou passos para confirmar. -->

## Antes de pedir revisão

- [ ] `./mvnw verify` passa no meu PC
- [ ] Endpoints e eventos iguais à proposta (ou proposta atualizada)
- [ ] DTO na API e nos eventos; nenhuma entidade exposta
- [ ] Erros no formato comum, com mensagem descritiva
- [ ] Listas paginadas
- [ ] Logs sem nome nem NUS de utentes
- [ ] Serviço acrescentado ao `docker-compose.yml` e ao `k8s/` (quando aplicável)
- [ ] README atualizado (portas, filas, routing keys)
- [ ] Revisão pedida a um elemento de outra equipa
