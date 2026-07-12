package com.uncomplex;

import com.uncomplex.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
@EnableScheduling
public class UncomplexApplication {

    public static void main(String[] args) {
        SpringApplication.run(UncomplexApplication.class, args);
    }
}
