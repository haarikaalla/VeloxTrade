package com.veloxtrade.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * VeloxTrade platform service: REST APIs, JWT authentication, live market
 * streaming and portfolio accounting for the simulated VLX market.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class VeloxTradeApplication {

    public static void main(String[] args) {
        SpringApplication.run(VeloxTradeApplication.class, args);
    }
}
