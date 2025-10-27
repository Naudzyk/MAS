package org.example.mas;

import jade.core.*;
import jade.core.Runtime;
import jade.wrapper.*;
import jade.wrapper.AgentContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


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
            p.setParameter(Profile.LOCAL_PORT, "1097"); // Изменяем порт

            AgentContainer container = rt.createMainContainer(p);

            // Запускаем веб-панель на порту 4567
            DashboardServer.start(4568, container);

            // Запускаем CoordinatorAgent
            logger.info("Creating CoordinatorAgent...");
            try {
                AgentController coordinatorController = container.createNewAgent("coordinator", CoordinatorAgent.class.getName(), null);
                logger.info("CoordinatorAgent created successfully");
                logger.info("Starting CoordinatorAgent...");
                coordinatorController.start();
                logger.info("CoordinatorAgent started successfully");
                
                // Проверяем, что агент действительно запущен
                Thread.sleep(1000); // Даем время агенту запуститься
                logger.info("CoordinatorAgent status: {}", coordinatorController.getState());
            } catch (Exception e) {
                logger.error("Failed to create or start CoordinatorAgent", e);
                throw e;
            }

            logger.info("System is ready. Open http://localhost:4567 to deploy cluster.");
            logger.info("Deployment will be started only after clicking 'Deploy' button.");

        } catch (Exception e) {
            logger.error("Failed to start system", e);
            System.exit(1);
        }
    }
}
