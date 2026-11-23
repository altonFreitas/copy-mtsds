package com.acessoatempo.incumprimento.config;

import com.acessoatempo.incumprimento.dtos.TransferenciaDTO;
import com.acessoatempo.incumprimento.models.Transferencia;
import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cria UM ModelMapper configurado para todo o serviço. Nas aulas fazia-se {@code new ModelMapper()} no
 * construtor de cada controller; aqui há três controllers com a mesma configuração, por isso define-se uma
 * vez como {@code @Bean} e injeta-se com {@code @Autowired}.
 */
@Configuration
public class ModelMapperConfig {

    @Bean
    public ModelMapper modelMapper() {
        ModelMapper modelMapper = new ModelMapper();

        // STRICT: só liga campos com o mesmo nome. Evita ligações erradas por ambiguidade
        // (ex.: o "estado" do DTO podia ser o estado da transferência OU o estado do caso).
        modelMapper.getConfiguration().setMatchingStrategy(MatchingStrategies.STRICT);

        // Campos do DTO que vêm de objetos ligados (caso e instituição) têm de ser indicados explicitamente.
        modelMapper.typeMap(Transferencia.class, TransferenciaDTO.class).addMappings(mapper -> {
            mapper.map(origem -> origem.getCaso().getId(), TransferenciaDTO::setCasoId);
            mapper.map(origem -> origem.getInstituicao().getId(), TransferenciaDTO::setInstituicaoId);
            mapper.map(origem -> origem.getInstituicao().getNome(), TransferenciaDTO::setInstituicaoNome);
        });
        return modelMapper;
    }
}
