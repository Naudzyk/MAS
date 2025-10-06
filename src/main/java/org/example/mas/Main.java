package org.example.mas;


import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;
import jade.wrapper.StaleProxyException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;

public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    private static final String[] PLAYBOOKS = {
            "01_system_preparation.yml",
            "02_containerd.yml",
            "03_kubernetes_install.yml",
            "04_kubernetes_init.yml",
            "05_calico_cni.yml",
            "06_worker_preparation.yml",
            "07_worker_join.yml",
            "08_htcondor.yml"
    };

    public static void main(String[] args) {
        CountDownLatch deploymentLatch = new CountDownLatch(1);

        try {
            logger.info("Starting HTCondor Multi-Agent Deployment System");

            if (args.length < 2) {
                printUsage();
                System.exit(1);
            }

            String inventoryPath = args[0];
            String workingDir = args[1];
            int timeoutMinutes = args.length > 2 ? Integer.parseInt(args[2]) : 30;

            if (!validatePaths(inventoryPath, workingDir)) {
                System.exit(1);
            }

            // Настройка JADE контейнера
            Runtime rt = Runtime.instance();
            Profile p = new ProfileImpl();
            p.setParameter(Profile.MAIN_HOST, "localhost");
            p.setParameter(Profile.GUI, "true"); // можно "false" в production
            p.setParameter(Profile.CONTAINER_NAME, "HTCondorDeployment");

            AgentContainer container = rt.createMainContainer(p);

            // Запуск координатора с latch'ем для синхронизации завершения
            AgentController coordinator = container.createNewAgent(
                    "coordinator-agent",
                    CoordinatorAgent.class.getName(),
                    new Object[]{ deploymentLatch, inventoryPath, workingDir, timeoutMinutes }
            );
            coordinator.start();

            // Запуск исполнительных агентов
            createAgents(container, inventoryPath, workingDir, timeoutMinutes);

            logger.info("All agents created. Deployment starting in 5 seconds...");

            // 🔑 Ожидаем завершения всего процесса
            deploymentLatch.await();

            logger.info("=== DEPLOYMENT PROCESS FINISHED ===");

        } catch (Exception e) {
            logger.error("System failed to start or execute", e);
            System.exit(1);
        }
    }

    private static void createAgents(AgentContainer container, String inventoryPath,
                                     String workingDir, int timeoutMinutes) throws Exception {

        createAgent(container, "system-prep-agent", AnsibleAgent.class.getName(),
                PLAYBOOKS[0], inventoryPath, timeoutMinutes, workingDir);

        createAgent(container, "containerd-agent", AnsibleAgent.class.getName(),
                PLAYBOOKS[1], inventoryPath, timeoutMinutes, workingDir);

        createAgent(container, "kubernetes-install-agent", AnsibleAgent.class.getName(),
                PLAYBOOKS[2], inventoryPath, timeoutMinutes, workingDir);

        createAgent(container, "kubernetes-init-agent", AnsibleAgent.class.getName(),
                PLAYBOOKS[3], inventoryPath, timeoutMinutes, workingDir);

        createAgent(container, "calico-agent", AnsibleAgent.class.getName(),
                PLAYBOOKS[4], inventoryPath, timeoutMinutes, workingDir);

        createAgent(container, "worker-prep-agent", AnsibleAgent.class.getName(),
                PLAYBOOKS[5], inventoryPath, timeoutMinutes, workingDir);

        createAgent(container, "worker-join-agent", AnsibleAgent.class.getName(),
                PLAYBOOKS[6], inventoryPath, timeoutMinutes, workingDir);

        createAgent(container, "htcondor-agent", AnsibleAgent.class.getName(),
                PLAYBOOKS[7], inventoryPath, timeoutMinutes, workingDir);
    }

    private static void createAgent(AgentContainer container, String agentName, String agentClass,
                                    String playbook, String inventoryPath, int timeoutMinutes,
                                    String workingDir) throws Exception {

        Object[] args = { playbook, inventoryPath, timeoutMinutes, workingDir };
        AgentController agent = container.createNewAgent(agentName, agentClass, args);
        agent.start();
        logger.info("Created agent: {} with playbook: {}", agentName, playbook);
    }

    private static boolean validatePaths(String inventoryPath, String workingDir) {
        if (!Files.exists(Paths.get(inventoryPath))) {
            logger.error("Inventory file not found: {}", inventoryPath);
            return false;
        }
        if (!Files.exists(Paths.get(workingDir))) {
            logger.error("Working directory not found: {}", workingDir);
            return false;
        }
        for (String playbook : PLAYBOOKS) {
            Path playbookPath = Paths.get(workingDir, playbook);
            if (!Files.exists(playbookPath)) {
                logger.error("Playbook not found: {}", playbookPath);
                return false;
            }
        }
        logger.info("All required files found successfully");
        return true;
    }

    private static void printUsage() {
        System.out.println("HTCondor Multi-Agent Deployment System\n");
        System.out.println("Usage: java -jar ansible-multi-agent.jar <inventory> <working_dir> [timeout_minutes]\n");
        System.out.println("Arguments:");
        System.out.println("  inventory        Path to Ansible inventory file");
        System.out.println("  working_dir      Directory containing playbooks");
        System.out.println("  timeout_minutes  Optional: Timeout for each playbook in minutes (default: 30)\n");
        System.out.println("Example:");
        System.out.println("  java -jar ansible-multi-agent.jar inventory.ini /path/to/playbooks");
        System.out.println("  java -jar ansible-multi-agent.jar inventory.ini /path/to/playbooks 45");
    }
}