package org.example.mas;

import jade.core.*;
import jade.core.Runtime;
import jade.wrapper.*;
import jade.wrapper.AgentContainer;

import java.nio.file.Files;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: java -jar MAS.jar <inventory> <playbooks_dir>");
            return;
        }

        String inventory = args[0];
        String playbooksDir = args[1];

        // Проверка файлов
        if (!Files.exists(Paths.get(inventory)) || !Files.exists(Paths.get(playbooksDir))) {
            throw new IllegalArgumentException("Inventory or playbooks dir not found");
        }

        Runtime rt = Runtime.instance();
        Profile p = new ProfileImpl();
        p.setParameter(Profile.MAIN_HOST, "localhost");
        p.setParameter(Profile.GUI, "false");

        AgentContainer container = rt.createMainContainer(p);

        DashboardServer.start(4567);

        // Запуск координатора
        container.createNewAgent("coordinator", CoordinatorAgent.class.getName(),
                new Object[]{inventory, playbooksDir}).start();

        System.out.println("MAS started. Coordinator is initializing cluster...");
        // Ожидание завершения не требуется — система работает постоянно
    }
}
