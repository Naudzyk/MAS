package org.example.mas;


import jade.wrapper.AgentContainer;
import jade.core.Runtime;
import jade.core.Profile;
import jade.core.ProfileImpl;
import org.example.mas.Service.Agent.CoordinatorAgent;
import org.example.mas.Service.Agent.StatusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.nio.file.Files;
import java.nio.file.Paths;

@SpringBootApplication
public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    public static void main(String[] args) {
        logger.info("Starting application");

        try {
            if(args.length < 2) {
                System.out.println("Usage: java -jar MAS.jar <inventory.ini> <scripts>");
                System.exit(1);
            }

            String inventory = Paths.get(args[0]).toAbsolutePath().toString();
            String scripts = Paths.get(args[1]).toAbsolutePath().toString();

            if(!Files.exists(Paths.get(scripts)) || !Files.exists(Paths.get(inventory))) {
                throw new Exception("Inventory file does not exist");
            }

            Runtime rt = Runtime.instance();
            Profile p = new ProfileImpl();
            p.setParameter(Profile.MAIN_HOST, "localhost");
            p.setParameter(Profile.GUI, "false");

            AgentContainer container = rt.createMainContainer(p);

            SpringApplication app = new SpringApplication(Main.class);
            app.addInitializers(applicationContext -> {
                applicationContext.getBeanFactory().registerSingleton("jadeContainer", container);
                applicationContext.getBeanFactory().registerSingleton("statusService", new StatusService());
            });

            app.run(args);

            logger.info("System is ready. Open http://localhost:8080 to deploy cluster.");
            logger.info("Deployment will be started only after clicking 'Deploy' button.");


            container.createNewAgent("coordinator", CoordinatorAgent.class.getName(),
                new Object[]{inventory, scripts}).start();
        } catch (Exception e){
            logger.error("Failed to start system", e);
            System.exit(1);

        }

    }
}
