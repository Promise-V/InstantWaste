import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class WasteFormApplication {
    public static void main(String[] args) {
        SpringApplication.run(WasteFormApplication.class, args);
        System.out.println("Waste Form API Server running on http://localhost:8080");
    }
}