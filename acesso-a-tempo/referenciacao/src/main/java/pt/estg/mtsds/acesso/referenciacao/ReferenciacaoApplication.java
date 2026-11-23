package pt.estg.mtsds.acesso.referenciacao;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableFeignClients
@SpringBootApplication
public class ReferenciacaoApplication {

	public static void main(String[] args) {
		SpringApplication.run(ReferenciacaoApplication.class, args);
	}
}
