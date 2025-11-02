package org.example.mas;

import jade.core.*;
import jade.core.Runtime;
import jade.wrapper.*;
import jade.wrapper.AgentContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Paths;


public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: java -jar MAS.jar <inventory> <playbooks_dir>");
            return;
        }

        String inventoryPath = Paths.get(args[0]).toAbsolutePath().toString();
        String workingDir = Paths.get(args[1]).toAbsolutePath().toString();

        // Проверка файлов
        if (!Files.exists(Paths.get(inventoryPath)) || !Files.exists(Paths.get(workingDir))) {
            throw new IllegalArgumentException("Inventory or playbooks dir not found");
        }

        Runtime rt = Runtime.instance();
        Profile p = new ProfileImpl();
        p.setParameter(Profile.MAIN_HOST, "localhost");
        p.setParameter(Profile.GUI, "false");

        AgentContainer container = rt.createMainContainer(p);

        DashboardServer.start(4567,container);

        // Запуск координатора
        container.createNewAgent("coordinator", CoordinatorAgent.class.getName(),
                new Object[]{inventoryPath, workingDir}).start();

        System.out.println("MAS started. Coordinator is initializing cluster...");
    }
}
