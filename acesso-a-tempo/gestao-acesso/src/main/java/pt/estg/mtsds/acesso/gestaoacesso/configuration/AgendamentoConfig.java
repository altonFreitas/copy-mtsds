package pt.estg.mtsds.acesso.gestaoacesso.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Liga as tarefas agendadas (@Scheduled) do serviço.
 */
@Configuration
@EnableScheduling
public class AgendamentoConfig {
}
