package com.acessoatempo.indicadores.services;

import com.acessoatempo.indicadores.data.CumprimentoProjection;
import com.acessoatempo.indicadores.data.EventosProcessadosRepository;
import com.acessoatempo.indicadores.data.IndicadoresRepository;
import com.acessoatempo.indicadores.dtos.CumprimentoDTO;
import com.acessoatempo.indicadores.dtos.ListaIndicadorDTO;
import com.acessoatempo.indicadores.dtos.ProjecaoDTO;
import com.acessoatempo.indicadores.dtos.TriagemDTO;
import com.acessoatempo.indicadores.exceptions.EventoInvalidoException;
import com.acessoatempo.indicadores.exceptions.ParametroInvalidoException;
import com.acessoatempo.indicadores.messages.EventoMessage;
import com.acessoatempo.indicadores.models.EventoProcessado;
import com.acessoatempo.indicadores.models.IndicadorSemanal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.util.List;

/**
 * Regras de negócio do serviço {@code indicadores}: (1) atualizar os contadores a partir dos eventos
 * e (2) calcular os indicadores pedidos pelos endpoints. Como na aula, o controller só recebe pedidos e
 * devolve respostas; a lógica fica aqui.
 */
@Service
public class IndicadoresService {

    private static final Logger log = LoggerFactory.getLogger(IndicadoresService.class);

    @Autowired
    private IndicadoresRepository indicadoresRepository;

    @Autowired
    private EventosProcessadosRepository eventosProcessadosRepository;

    // ------------------------------------------------------------------------------------------
    // 1) Processamento de eventos (chamado pelo EventosListener)
    // ------------------------------------------------------------------------------------------

    /**
     * Processa um evento. {@code @Transactional}: a atualização do contador e o registo do evento como
     * "processado" acontecem na mesma transação; ou ficam os dois gravados ou nenhum.
     */
    @Transactional
    public void processar(EventoMessage evento) {
        if (evento == null || evento.getEventoId() == null || evento.getTipo() == null
                || evento.getDataHoraEvento() == null) {
            throw new EventoInvalidoException("Evento sem eventoId, tipo (routing key) ou dataHoraEvento");
        }

        // Idempotência: o RabbitMQ pode entregar o mesmo evento mais do que uma vez.
        if (eventosProcessadosRepository.existsById(evento.getEventoId())) {
            log.info("Evento {} ({}) já processado: ignorado", evento.getEventoId(), evento.getTipo());
            return;
        }

        switch (evento.getTipo()) {
            case "episodio.inscrito" -> {
                IndicadorSemanal linha = linhaDe(evento);
                linha.setEntradas(linha.getEntradas() + 1);
                indicadoresRepository.save(linha);
            }
            case "episodio.saida" -> {
                IndicadorSemanal linha = linhaDe(evento);
                linha.setSaidas(linha.getSaidas() + 1);
                indicadoresRepository.save(linha);
            }
            case "tmrg.incumprido" -> {
                IndicadorSemanal linha = linhaDe(evento);
                linha.setIncumpridos(linha.getIncumpridos() + 1);
                indicadoresRepository.save(linha);
            }
            case "pedido.triado" -> {
                IndicadorSemanal linha = linhaDe(evento);
                LocalDate dataPedido = dataPedidoDe(evento);
                long dias = ChronoUnit.DAYS.between(dataPedido, evento.getDataHoraEvento().toLocalDate());
                linha.setTriagens(linha.getTriagens() + 1);
                linha.setDiasTriagemTotal(linha.getDiasTriagemTotal() + Math.max(dias, 0));
                indicadoresRepository.save(linha);
            }
            default ->
                // Os restantes eventos (pedido.referenciado, consulta.*, transferencia.*...) fazem parte
                // do contrato dos 17 eventos, mas ainda não alteram nenhum contador (Fase 2).
                log.debug("Evento {} sem efeito nos contadores atuais", evento.getTipo());
        }

        eventosProcessadosRepository.save(
                new EventoProcessado(evento.getEventoId(), evento.getTipo(), LocalDateTime.now()));
    }

    /** Encontra (ou cria) a linha da especialidade e semana a que o evento diz respeito. */
    private IndicadorSemanal linhaDe(EventoMessage evento) {
        String esp = evento.getEspecialidade();
        if (esp == null || esp.isBlank()) {
            throw new EventoInvalidoException("Evento " + evento.getTipo() + " sem 'especialidade'");
        }
        // A semana vem da data/hora do evento (envelope do grupo: dataHoraEvento).
        String semana = semanaIso(evento.getDataHoraEvento());
        return indicadoresRepository.findByEspecialidadeAndSemana(esp, semana)
                .orElseGet(() -> new IndicadorSemanal(esp, semana));
    }

    /** Data em que o pedido foi registado (campo {@code dataPedido} do evento {@code pedido.triado}). */
    private LocalDate dataPedidoDe(EventoMessage evento) {
        if (evento.getDataPedido() == null) {
            throw new EventoInvalidoException("Evento " + evento.getTipo() + " sem 'dataPedido'");
        }
        return evento.getDataPedido();
    }

    /** Converte uma data na semana ISO, no formato 2026-W47. */
    static String semanaIso(LocalDateTime data) {
        int ano = data.get(IsoFields.WEEK_BASED_YEAR);
        int semana = data.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        return String.format("%d-W%02d", ano, semana);
    }

    // ------------------------------------------------------------------------------------------
    // 2) Cálculo dos indicadores (chamado pelo IndicadoresController)
    // ------------------------------------------------------------------------------------------

    /** Tamanho da lista, entradas e saídas. Sem {@code semana}, usa todo o período. */
    @Transactional(readOnly = true)
    public ListaIndicadorDTO lista(String especialidade, String semana) {
        exigirEspecialidade(especialidade);
        if (semana != null && !semana.matches("\\d{4}-W\\d{2}")) {
            throw new ParametroInvalidoException("Semana inválida: use o formato 2026-W47");
        }
        List<IndicadorSemanal> linhas = indicadoresRepository.findByEspecialidade(especialidade);

        long entradas = 0;
        long saidas = 0;
        long entradasTotal = 0;
        long saidasTotal = 0;
        for (IndicadorSemanal l : linhas) {
            // Comparar texto "2026-W05" funciona porque o formato é ordenável alfabeticamente.
            if (semana == null || l.getSemana().compareTo(semana) <= 0) {
                entradasTotal += l.getEntradas();
                saidasTotal += l.getSaidas();
            }
            if (semana == null || l.getSemana().equals(semana)) {
                entradas += l.getEntradas();
                saidas += l.getSaidas();
            }
        }
        return new ListaIndicadorDTO(especialidade, semana, entradas, saidas, entradasTotal - saidasTotal);
    }

    /** Taxa de cumprimento por especialidade num período (ano "2026" ou semana "2026-W47"). */
    @Transactional(readOnly = true)
    public List<CumprimentoDTO> cumprimento(String periodo) {
        if (periodo == null || !periodo.matches("\\d{4}(-W\\d{2})?")) {
            throw new ParametroInvalidoException("Período inválido: use um ano (2026) ou uma semana (2026-W47)");
        }
        return indicadoresRepository.cumprimentoPorEspecialidade(periodo).stream()
                .map(this::paraCumprimentoDTO)
                .toList();
    }

    private CumprimentoDTO paraCumprimentoDTO(CumprimentoProjection p) {
        long entradas = p.getEntradas() == null ? 0 : p.getEntradas();
        long incumpridos = p.getIncumpridos() == null ? 0 : p.getIncumpridos();
        double taxa = entradas == 0 ? 100.0 : 100.0 * (entradas - incumpridos) / entradas;
        return new CumprimentoDTO(p.getEspecialidade(), entradas, incumpridos, arredondar(taxa));
    }

    /** Tempo médio entre o pedido e a triagem, por especialidade. */
    @Transactional(readOnly = true)
    public TriagemDTO triagem(String especialidade) {
        exigirEspecialidade(especialidade);
        long triagens = 0;
        long dias = 0;
        for (IndicadorSemanal l : indicadoresRepository.findByEspecialidade(especialidade)) {
            triagens += l.getTriagens();
            dias += l.getDiasTriagemTotal();
        }
        double media = triagens == 0 ? 0.0 : (double) dias / triagens;
        return new TriagemDTO(especialidade, triagens, arredondar(media));
    }

    /** Projeção do tamanho da lista daqui a {@code semanas} semanas, com base nas médias semanais. */
    @Transactional(readOnly = true)
    public ProjecaoDTO projecao(String especialidade, int semanas) {
        exigirEspecialidade(especialidade);
        if (semanas < 1 || semanas > 52) {
            throw new ParametroInvalidoException("O número de semanas deve estar entre 1 e 52");
        }
        List<IndicadorSemanal> linhas = indicadoresRepository.findByEspecialidade(especialidade);
        long entradas = linhas.stream().mapToLong(IndicadorSemanal::getEntradas).sum();
        long saidas = linhas.stream().mapToLong(IndicadorSemanal::getSaidas).sum();
        long tamanhoAtual = entradas - saidas;
        double mediaEntradas = linhas.isEmpty() ? 0.0 : (double) entradas / linhas.size();
        double mediaSaidas = linhas.isEmpty() ? 0.0 : (double) saidas / linhas.size();
        double projetado = Math.max(0.0, tamanhoAtual + semanas * (mediaEntradas - mediaSaidas));
        return new ProjecaoDTO(especialidade, tamanhoAtual, arredondar(mediaEntradas),
                arredondar(mediaSaidas), semanas, arredondar(projetado));
    }

    private void exigirEspecialidade(String especialidade) {
        if (especialidade == null || especialidade.isBlank()) {
            throw new ParametroInvalidoException("O parâmetro 'esp' (especialidade) é obrigatório");
        }
    }

    private double arredondar(double valor) {
        return Math.round(valor * 100.0) / 100.0;
    }
}
