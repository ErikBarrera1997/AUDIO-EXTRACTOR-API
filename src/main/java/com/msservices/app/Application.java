package com.msservices.app;

import com.msservices.app.config.YtdlpProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class Application {

	private static final Logger log = LoggerFactory.getLogger(Application.class);

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

	@Bean
	public CommandLineRunner serverInitialized(YtdlpProperties ytdlpProperties) {
		return args -> {
			log.info("Server initialized");
			String maskedProxy = ytdlpProperties.getMaskedProxy();
			if (maskedProxy.isEmpty()) {
				log.info("yt-dlp proxy: not configured, requests use the server IP directly");
			} else {
				log.info("yt-dlp proxy: {}", maskedProxy);
			}
		};
	}

}
