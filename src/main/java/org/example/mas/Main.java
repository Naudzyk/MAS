package org.example.mas;

import jade.core.*;
import jade.core.Runtime;
import jade.wrapper.*;
import jade.wrapper.AgentContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Watchable;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    public static void main(String[] args) throws Exception {
        try {
            logger.info("Starting HTCondor Multi-Agent Deployment System (Dashboard Mode)");

            // Запускаем JADE-контейнер
            Runtime rt = Runtime.instance();
            Profile p = new ProfileImpl();
            p.setParameter(Profile.MAIN_HOST, "localhost");
            p.setParameter(Profile.GUI, "false");

            AgentContainer container = rt.createMainContainer(p);

            DashboardServer.start(4567, container);

            container.createNewAgent("coordinator", CoordinatorAgent.class.getName(), null).start();

            logger.info("System is ready. Open http://localhost:4567 to deploy cluster.");
            logger.info("Deployment will be started only after clicking 'Deploy' button.");

        } catch (Exception e) {
            logger.error("Failed to start system", e);
            System.exit(1);
        }
    }
}

