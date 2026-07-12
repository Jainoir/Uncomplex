package com.uncomplex;

import com.uncomplex.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class UncomplexApplication {

    public static void main(String[] args) {
        SpringApplication.run(UncomplexApplication.class, args);
    }
}
