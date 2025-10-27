package org.example.mas;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.wrapper.AgentController;
import org.example.mas.Agent.MasterAgent;
import org.example.mas.utils.AnsibleRunner;
import org.example.mas.utils.InventoryParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CoordinatorAgent extends Agent {
    private static final Logger logger = LoggerFactory.getLogger(CoordinatorAgent.class);
    private String inventoryPath; // Путь к inventory.ini, полученный от DashboardServer
    private String varsPath;      // Путь к vars.yml, полученный от DashboardServer
    private String playbooksDir;  // Путь к папке scripts, полученный от DashboardServer
    private final Map<String, AID> nodeAgents = new ConcurrentHashMap<>();

    @Override
    protected void setup() {
        logger.info("CoordinatorAgent (JADE 4.5) initializing...");

        DashboardServer.updateStatus("ansibleStage", "WAITING_FOR_DEPLOY_COMMAND");
        DashboardServer.updateStatus("clusterStatus", "NOT_DEPLOYED");
        DashboardServer.updateStatus("alerts", new String[0]);

        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                Object cmd = getO2AObject();
                if (cmd != null) {
                    logger.info("CoordinatorAgent: Received O2A object of type: {}", cmd.getClass().getSimpleName());
                    if (cmd instanceof String) {
                        String command = (String) cmd;
                        logger.info("CoordinatorAgent: Processing command: {}", command);

                        if (command.startsWith("DEPLOY:")) {
                            String[] parts = command.substring(7).split(",", 3);
                            logger.debug("DEPLOY command parts: {}", java.util.Arrays.toString(parts));
                            if (parts.length == 3) {
                                String invPath = parts[0].trim();
                                String vPath = parts[1].trim();
                                String pbDir = parts[2].trim();
                                logger.info("Initiating deployment with inventory: {}, vars: {}, playbooks dir: {}", invPath, vPath, pbDir);
                                startDeployment(invPath, vPath, pbDir);
                            } else {
                                String errorMsg = "Invalid DEPLOY command format. Expected 'DEPLOY:path1,path2,path3', got: " + command;
                                logger.error(errorMsg);
                                sendAlert("Deployment failed: " + errorMsg);
                                DashboardServer.updateStatus("clusterStatus", "DEPLOYMENT_FAILED_INVALID_COMMAND");
                            }
                        } else if ("COMMAND: COLLECT_DIAGNOSTIC_LOGS".equals(command)) {
                            logger.info("Initiating diagnostic logs collection");
                            collectDiagnosticLogs();
                        } else if ("TEST_COMMAND".equals(command)) {
                            logger.info("Received TEST_COMMAND - updating status");
                            DashboardServer.updateStatus("testStatus", "TEST_COMMAND_RECEIVED");
                            logger.info("Status updated to: TEST_COMMAND_RECEIVED");
                            sendAlert("TEST_COMMAND processed by CoordinatorAgent");
                        } else {
                            String warnMsg = "Unknown O2A command received: " + command;
                            logger.warn(warnMsg);
                            sendAlert("Warning: " + warnMsg);
                        }
                    } else {
                         String warnMsg = "Received unexpected O2A object type: " + cmd.getClass().getSimpleName();
                         logger.warn(warnMsg);
                         sendAlert("Warning: " + warnMsg);
                    }
                }
                block();
            }
        });

        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                ACLMessage msg = receive();
                if (msg != null) {
                    handleNodeMessage(msg);
                } else {
                    block();
                }
            }
        });

        logger.info("CoordinatorAgent (JADE 4.5) initialized and waiting for O2A commands.");
    }

    /**
     * Запускает процесс развёртывания кластера.
     * @param inventoryPath Путь к файлу inventory.ini
     * @param varsPath Путь к файлу vars.yml
     * @param playbooksDir Путь к директории с Ansible плейбуками (scripts)
     */
    private void startDeployment(String inventoryPath, String varsPath, String playbooksDir) {
        logger.info("Starting deployment sequence...");
        logger.info("Inventory file: {}", inventoryPath);
        logger.info("Vars file: {}", varsPath);
        logger.info("Playbooks directory: {}", playbooksDir);

        if (!validatePaths(inventoryPath, playbooksDir)) { // varsPath - временный файл, может не существовать как файл на диске, но Ansible его использует
             String errorMsg = "Deployment failed: Invalid paths provided by DashboardServer.";
             logger.error(errorMsg);
             sendAlert(errorMsg);
             DashboardServer.updateStatus("ansibleStage", "ERROR: Invalid paths");
             DashboardServer.updateStatus("clusterStatus", "DEPLOYMENT_FAILED_INVALID_PATHS");
             return;
        }

        this.inventoryPath = inventoryPath;
        this.varsPath = varsPath;
        this.playbooksDir = playbooksDir;

        new Thread(() -> {
            try {
                logger.info("Deployment thread started.");
                DashboardServer.updateStatus("ansibleStage", "🚀 Deployment started...");
                DashboardServer.updateStatus("clusterStatus", "DEPLOYING");

                // 3. Копируем временный vars.yml в директорию playbooks
                // Это необходимо, так как Ansible обычно ожидает vars.yml внутри своей рабочей директории
                logger.info("Copying temporary vars.yml from {} to {}", varsPath, playbooksDir);
                try {
                    Path sourceVars = Paths.get(varsPath);
                    Path targetVars = Paths.get(playbooksDir, "vars.yml");
                    // Используем REPLACE_EXISTING на случай, если файл уже существует
                    Files.copy(sourceVars, targetVars, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    logger.info("vars.yml copied successfully to playbooks directory");
                } catch (Exception e) {
                    String errorMsg = "Failed to copy vars.yml to playbooks directory: " + e.getMessage();
                    logger.error(errorMsg, e);
                    sendAlert(errorMsg);
                    DashboardServer.updateStatus("ansibleStage", "ERROR: Failed to copy vars.yml");
                    DashboardServer.updateStatus("clusterStatus", "DEPLOYMENT_FAILED_COPY_VARS");
                    return;
                }

                // 4. Список плейбуков для выполнения по порядку
                String[] playbooks = {
                    "01_system_preparation.yml",
                    "02_containerd.yml",
                    "03_kubernetes_install.yml",
                    "04_kubernetes_init.yml",
                    "05_calico_cni.yml",
                    "06_worker_preparation.yml",
                    "07_worker_join.yml",
                    "08_htcondor.yml"
                };

                // 5. Последовательное выполнение плейбуков
                for (String playbook : playbooks) {
                    logger.info("Running Ansible playbook: {}", playbook);
                    DashboardServer.updateStatus("ansibleStage", " Running: " + playbook);

                    // Выполняем плейбук с таймаутом 15 минут (или другим, по усмотрению)
                    AnsibleRunner.AnsibleResult result = AnsibleRunner.run(playbook, inventoryPath, playbooksDir, 15);

                    if (!result.success) {
                        String errorMsg = "Deployment failed at playbook '" + playbook + "': " + result.errorCode;
                        logger.error("{} - Details: {}", errorMsg, result.details);
                        sendAlert(errorMsg);
                        DashboardServer.updateStatus("ansibleStage", " ERROR: " + playbook);
                        DashboardServer.updateStatus("clusterStatus", "DEPLOYMENT_FAILED_PLAYBOOK");
                        return;
                    }
                    logger.info(" Playbook '{}' completed successfully.", playbook);
                }

                // 6. Все плейбуки выполнены успешно
                logger.info(" All Ansible playbooks executed successfully. Deployment complete.");
                DashboardServer.updateStatus("ansibleStage", " ALL_STAGES_COMPLETED");
                DashboardServer.updateStatus("clusterStatus", "READY");
                DashboardServer.updateStatus("alerts", new String[]{"Deployment successful!"}); // Информационное сообщение

                // 7. Создаём агенты мониторинга (MasterAgent'ы)
                createNodeAgents();

            } catch (Exception e) {
                String errorMsg = "Deployment process crashed unexpectedly: " + e.getMessage();
                logger.error(" {}", errorMsg, e);
                sendAlert(errorMsg);
                DashboardServer.updateStatus("ansibleStage", " CRASHED");
                DashboardServer.updateStatus("clusterStatus", "DEPLOYMENT_CRASHED");
            }
        }, "DeploymentThread-" + System.currentTimeMillis()).start(); // Имя потока для удобства отладки
    }

    /**
     * Проверяет существование необходимых файлов и директорий.
     */
    private boolean validatePaths(String inventoryPath, String playbooksDir) {
        logger.info("Validating deployment paths...");
        boolean isValid = true;

        if (!Files.exists(Paths.get(inventoryPath))) {
            logger.error(" Inventory file NOT FOUND: {}", inventoryPath);
            isValid = false;
        } else {
            logger.info(" Inventory file found: {}", inventoryPath);
        }

        if (!Files.exists(Paths.get(playbooksDir))) {
            logger.error(" Playbooks directory NOT FOUND: {}", playbooksDir);
            isValid = false;
        } else {
            logger.info(" Playbooks directory found: {}", playbooksDir);
        }

        return isValid;
    }

    private void createNodeAgents() {
        try {
            logger.info("Creating monitoring agents...");
            InventoryParser.Inventory inv = InventoryParser.parse(this.inventoryPath);

            // Создаём MasterAgent только для узлов группы central_manager
            for (InventoryParser.Host master : inv.getGroup("central_manager")) {
                 createNodeAgent(master.name, true);
            }


            logger.info("Monitoring agents creation process finished.");
        } catch (Exception e) {
            String errorMsg = "Failed to create monitoring agents: " + e.getMessage();
            logger.error("{}", errorMsg, e);
            sendAlert(errorMsg);
        }
    }

    /**
     * Создаёт и запускает одного агента (MasterAgent) для конкретного узла.
     */
    private void createNodeAgent(String nodeName, boolean isMaster) {
        try {
            logger.info("Creating {} agent for node: {}", isMaster ? "MasterAgent" : "NodeAgent", nodeName);
            String agentType = MasterAgent.class.getName();
            Object[] agentArgs = new Object[]{nodeName, this.inventoryPath, this.playbooksDir};

            AgentController ac = getContainerController().createNewAgent(nodeName, agentType, agentArgs);
            ac.start();

            AID agentAID = new AID(nodeName, AID.ISLOCALNAME);
            nodeAgents.put(nodeName, agentAID);

            logger.info("Successfully created and started {} agent: {}", isMaster ? "Master" : "Node", nodeName);
        } catch (Exception e) {
            String errorMsg = "Failed to create/start agent for node '" + nodeName + "': " + e.getMessage();
            logger.error(" {}", errorMsg, e);
            sendAlert(errorMsg);
        }
    }

    /**
     * Обрабатывает входящие ACL сообщения от других агентов.
     */
    private void handleNodeMessage(ACLMessage msg) {
        String sender = msg.getSender().getLocalName();
        String content = msg.getContent();

        if (content != null) {
            if (content.startsWith("ALERT:")) {
                logger.warn(" Received ALERT from '{}': {}", sender, content);
                // Передаём алерт на веб-панель
                DashboardServer.updateStatus("alerts", new String[]{content});
            } else if ("READY".equals(content)) {
                logger.info(" Node '{}' reported ready.", sender);
                // Можно обновить статус узла или выполнить дальнейшие действия
            } else {
                 logger.debug("Received message from '{}': {}", sender, content);
            }
        } else {
             logger.debug("Received empty message from '{}'", sender);
        }
    }

    /**
     * Отправляет сообщение-оповещение на веб-панель.
     */
    private void sendAlert(String message) {
        logger.warn(" SENDING ALERT: {}", message);
        // Получаем текущие алерты
        Object currentAlertsObj = DashboardServer.STATUS.get("alerts");
        String[] currentAlerts;
        if (currentAlertsObj instanceof String[]) {
            currentAlerts = (String[]) currentAlertsObj;
        } else {
            currentAlerts = new String[0];
        }

        // Создаём новый массив с добавленным сообщением
        String[] newAlerts = new String[currentAlerts.length + 1];
        System.arraycopy(currentAlerts, 0, newAlerts, 0, currentAlerts.length);
        newAlerts[newAlerts.length - 1] = message;

        // Обновляем статус
        DashboardServer.updateStatus("alerts", newAlerts);
    }

    /**
     * Собирает диагностические логи (заглушка/пример).
     */
    private void collectDiagnosticLogs() {
        logger.info("Starting diagnostic logs collection (stub implementation)...");
        try {
            Path logPath = Paths.get(System.getProperty("user.dir"), "collected-diagnostic-logs.txt");
            logger.info("Saving diagnostic logs to: {}", logPath.toAbsolutePath());

            String systemInfo = "System: " + System.getProperty("os.name") + " " + System.getProperty("os.version");
            String javaInfo = "Java: " + System.getProperty("java.version");
            String timestamp = "Collected at: " + java.time.Instant.now();

            String fullLog = "=== SYSTEM INFO ===\n" + systemInfo +
                    "\n\n=== JAVA INFO ===\n" + javaInfo +
                    "\n\n=== COLLECTION TIMESTAMP ===\n" + timestamp +
                    "\n\n=== DIAGNOSTIC LOGS (Stub) ===\n" +
                    "This is a stub implementation.\n" +
                    "In a full implementation, this would gather:\n" +
                    "- Kubelet logs\n" +
                    "- Containerd status (`systemctl status containerd`)\n" +
                    "- Kubernetes pod status (`kubectl get pods -A`)\n" +
                    "- System logs (`journalctl -u kubelet`)\n" +
                    "- HTCondor status (`condor_status`)\n";

            Files.write(logPath, fullLog.getBytes());
            logger.info(" Diagnostic logs (stub) saved successfully to: {}", logPath.toAbsolutePath());
            // Сообщаем веб-панели о новом файле логов
            DashboardServer.updateStatus("diagnosticLogs", logPath.toAbsolutePath().toString());
            sendAlert("Diagnostic logs collected and saved.");

        } catch (Exception e) {
            String errorMsg = "Failed to collect diagnostic logs: " + e.getMessage();
            logger.error(" {}", errorMsg, e);
            sendAlert(errorMsg);
            DashboardServer.updateStatus("diagnosticLogs", ""); // Очищаем путь, если ошибка
        }
    }


}