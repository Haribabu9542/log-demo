package com.java.logdemo;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@SpringBootApplication
@RestController
public class LogDemoApplication {

    private static final Logger logger = LogManager.getLogger(LogDemoApplication.class);

    public static void main2(String[] args) {
        System.out.println("helsfdk");
    }

    public static void main(String[] args) {
        SpringApplication.run(LogDemoApplication.class, args);
    }

    @RequestMapping("/")
    String index() {
        logger.debug("This is a debug message");
        logger.info("This is an info message");
        logger.warn("This is a warn message");
        logger.error("This is an error message");
        return "index";
    }

}
