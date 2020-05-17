package com.fluxtest.transactions;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import com.fluxtest.fluxtestcommon.IdHolder;
import com.fluxtest.fluxtestcommon.LateFeeEntity;
import com.fluxtest.fluxtestcommon.SomeEntity;
import com.fluxtest.transactions.repository.LateFeeEntityRepository;
import com.fluxtest.transactions.repository.SomeEntityRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@SpringBootApplication
@RestController
@RequestMapping("/")
@EntityScan("com.fluxtest.fluxtestcommon")
public class TransactionsApplication implements CommandLineRunner {

	public static void main(String[] args) {
		SpringApplication.run(TransactionsApplication.class, args);
	}

	@Override
	public void run(String... args) throws Exception {
		Thread.sleep(1000);
		Flux.range(0, 1000)
		.flatMap(i->{
			return transactionClient
			.post()
			.bodyValue(SomeEntity.builder().id(Long.valueOf(i)).timestamp(LocalDateTime.now()).build())
			.retrieve()
			.bodyToMono(UUID.class);
		})
		.delayElements(Duration.ofMillis(100))
		.subscribe(u->System.out.print('.'));
	}
	
	@Bean("lateFeeClient")
	WebClient getLateFeeClient() {
		return WebClient.create("http://localhost:8090/latefee");
	}
	@Bean("transactionClient")
	WebClient getTransactionClient() {
		return WebClient.create("http://localhost:8080/transaction");
	}

	@Autowired
	@Qualifier("lateFeeClient")
	private WebClient lateFeeClient;
	@Autowired
	@Qualifier("transactionClient")
	private WebClient transactionClient;
	@Autowired
	private SomeEntityRepository someEntityRepo;
	@Autowired
	private LateFeeEntityRepository lateFeeEntityRepo;
	@PostMapping(value="transaction", consumes=MediaType.APPLICATION_JSON_VALUE, produces=MediaType.APPLICATION_JSON_VALUE)
	Mono<UUID> postTransaction(@RequestBody SomeEntity someEntity) {
		return Mono.just(someEntity)
			.flatMap(entity->{
				someEntityRepo.save(someEntity);
				return lateFeeClient
					.post()
					.bodyValue(IdHolder.builder().id(entity.getId()).build())
					.retrieve()
					.bodyToMono(UUID.class)
					.flatMap(uid->Mono.just(uid));
			});
	}
	@GetMapping(value="transaction/{id}", produces=MediaType.APPLICATION_JSON_VALUE)
	Mono<SomeEntity> postTransaction(@PathVariable Long id) {
		return Mono.just(someEntityRepo.findById(id).get());
	}
	@PostMapping(value="latefee", consumes=MediaType.APPLICATION_JSON_VALUE, produces=MediaType.APPLICATION_JSON_VALUE)
	Mono<UUID> postLateFee(@RequestBody LateFeeEntity lateFeeEntity) {
		return Mono.just(lateFeeEntity)
		.flatMap(entity->{
			lateFeeEntityRepo.save(lateFeeEntity);
			return Mono.just(UUID.randomUUID());
		});
	}
	@GetMapping(value="transactions", produces=MediaType.APPLICATION_JSON_VALUE)
	Flux<Long> getTransactions() {
		return Flux.fromStream(someEntityRepo.findAll()
				.stream().map(se->
				lateFeeEntityRepo.findById(se.getId())
				.map(lfe->Duration.between(se.getTimestamp(), lfe.getTimestamp()).toMillis())
				.orElseThrow(()->new IllegalStateException("nf"))
				));
	}
}
