package com.instantwaste;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = "com.instantwaste")
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
        System.out.println("\n✅ Instant Waste API Server Started!");
        System.out.println("🌐 API running at: http://localhost:8080");
        System.out.println("📝 Health check: http://localhost:8080/api/health\n");
    }
}