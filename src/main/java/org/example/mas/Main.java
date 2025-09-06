package org.example.mas;


import jade.core.Runtime;
import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;
import org.example.mas.Agent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    // Пути к playbook'ам (относительно рабочей директории)
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
        try {
            logger.info("Starting HTCondor Multi-Agent Deployment System");

            // Проверяем аргументы командной строки
            if (args.length < 2) {
                printUsage();
                System.exit(1);
            }

            String inventoryPath = args[0];
            String workingDir = args[1];
            int timeoutMinutes = args.length > 2 ? Integer.parseInt(args[2]) : 30;

            // Проверяем существование файлов
            if (!validatePaths(inventoryPath, workingDir)) {
                System.exit(1);
            }

            // Запускаем JADE платформу
            Runtime rt = Runtime.instance();
            Profile p = new ProfileImpl();
            p.setParameter(Profile.MAIN_HOST, "localhost");
            p.setParameter(Profile.GUI, "true");
            p.setParameter(Profile.CONTAINER_NAME, "HTCondorDeployment");

            AgentContainer container = rt.createMainContainer(p);

            // Создаем координатора
            AgentController coordinator = container.createNewAgent(
                "coordinator-agent",
                CoordinatorAgent.class.getName(),
                null
            );
            coordinator.start();

            // Создаем агентов для каждого playbook
            createAgents(container, inventoryPath, workingDir,  timeoutMinutes);

            logger.info("All agents created successfully. Deployment will start in 5 seconds...");


        } catch (Exception e) {
            logger.error("Failed to start multi-agent system", e);
            System.exit(1);
        }
    }

    /**
     * Создает всех агентов для развертывания
     */
    private static void createAgents(AgentContainer container, String inventoryPath,
                                   String workingDir, int timeoutMinutes)
                                   throws Exception {

        // Агент подготовки системы
        createAgent(container, "system-prep-agent", SystemPreparationAgent.class.getName(),
                   PLAYBOOKS[0], inventoryPath, timeoutMinutes, workingDir);

        // Агент containerd
        createAgent(container, "containerd-agent", ContainerdAgent.class.getName(),
                   PLAYBOOKS[1], inventoryPath, timeoutMinutes, workingDir);

        // Агент установки Kubernetes
        createAgent(container, "kubernetes-install-agent", KubernetesInstallAgent.class.getName(),
                   PLAYBOOKS[2], inventoryPath, timeoutMinutes, workingDir);

        // Агент инициализации Kubernetes
        createAgent(container, "kubernetes-init-agent", KubernetesInitAgent.class.getName(),
                   PLAYBOOKS[3], inventoryPath, timeoutMinutes, workingDir);

        // Агент Calico CNI
        createAgent(container, "calico-agent", CalicoAgent.class.getName(),
                   PLAYBOOKS[4], inventoryPath, timeoutMinutes, workingDir);

        // Агент подготовки worker'ов
        createAgent(container, "worker-prep-agent", WorkerPreparationAgent.class.getName(),
                   PLAYBOOKS[5], inventoryPath, timeoutMinutes, workingDir);

        // Агент присоединения worker'ов
        createAgent(container, "worker-join-agent", WorkerJoinAgent.class.getName(),
                   PLAYBOOKS[6], inventoryPath, timeoutMinutes, workingDir);

        // Агент HTCondor
        createAgent(container, "htcondor-agent", HTCondorAgent.class.getName(),
                   PLAYBOOKS[7], inventoryPath, timeoutMinutes, workingDir);
    }

    /**
     * Создает одного агента
     */
    private static void createAgent(AgentContainer container, String agentName, String agentClass,
                                    String playbook, String inventoryPath, int timeoutMinutes, String workingDir) throws Exception {

        Object[] args = {
            playbook,
            inventoryPath,
            timeoutMinutes,
            workingDir
        };

        AgentController agent = container.createNewAgent(agentName, agentClass, args);
        agent.start();

        logger.info("Created agent: {} with playbook: {}", agentName, playbook);
    }

    /**
     * Проверяет существование необходимых файлов
     */
    private static boolean validatePaths(String inventoryPath, String workingDir) {
        // Проверяем inventory файл
        if (!Files.exists(Paths.get(inventoryPath))) {
            logger.error("Inventory file not found: {}", inventoryPath);
            return false;
        }

        // Проверяем рабочую директорию
        if (!Files.exists(Paths.get(workingDir))) {
            logger.error("Working directory not found: {}", workingDir);
            return false;
        }

        // Проверяем наличие всех playbook'ов
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

    /**
     * Выводит справку по использованию
     */
    private static void printUsage() {
        System.out.println("HTCondor Multi-Agent Deployment System");
        System.out.println();
        System.out.println("Usage: java -jar ansible-multi-agent.jar <inventory> <working_dir> [extra_vars] [use_wsl] [timeout_minutes]");
        System.out.println();
        System.out.println("Arguments:");
        System.out.println("  inventory        Path to Ansible inventory file");
        System.out.println("  working_dir      Directory containing playbooks");
        System.out.println("  extra_vars       Optional: Extra variables for Ansible (JSON format)");
        System.out.println("  use_wsl          Optional: Use WSL for Windows (true/false, default: false)");
        System.out.println("  timeout_minutes  Optional: Timeout for each playbook in minutes (default: 30)");
        System.out.println();
        System.out.println("Example:");
        System.out.println("  java -jar ansible-multi-agent.jar inventory.ini /path/to/playbooks");
        System.out.println("  java -jar ansible-multi-agent.jar inventory.ini /path/to/playbooks '{\"env\":\"prod\"}' true 45");
    }
}