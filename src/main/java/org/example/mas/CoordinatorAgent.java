package org.example.mas;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.lang.acl.ACLMessage;
import jade.wrapper.AgentController;
import org.example.mas.Agent.MasterAgent;
import org.example.mas.utils.AnsibleRunner;
import org.example.mas.utils.InventoryParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class CoordinatorAgent extends Agent {
    private static final Logger logger = LoggerFactory.getLogger(CoordinatorAgent.class);
    private String inventory;
    private String playbooksDir;
    private final Map<String, AID> nodeAgents = new HashMap<>();

    @Override
    protected void setup() {
        Object[] args = getArguments();
        if (args == null || args.length < 2) {
            logger.error("CoordinatorAgent requires: inventoryPath, playbooksDir");
            doDelete();
            return;
        }

        this.inventory = (String) args[0];
        this.playbooksDir = (String) args[1];
        logger.info("CoordinatorAgent initialized with inventory: {}, playbooksDir: {}", inventory, playbooksDir);

        // === ФАЗА 1: Инициализация кластера (однократно) ===
        addBehaviour(new OneShotBehaviour() {
            @Override
            public void action() {
                logger.info("Starting initial cluster deployment...");

                // Запускаем Ansible-плейбуки для полной настройки
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

                for (String playbook : playbooks) {
                    logger.info("Running playbook: {}", playbook);
                    DashboardServer.updateStatus("ansibleStage",playbook);
                    AnsibleRunner.AnsibleResult result = AnsibleRunner.run(playbook, inventory, playbooksDir, 15);

                    if (!result.success) {
                        handlePlaybookFailure(playbook, result);
                        return; // или продолжить с откатом
                    }
                }

                logger.info("Initial cluster deployment completed. Creating node agents...");
                DashboardServer.updateStatus("ansibleStage", "kubernetes_init ✅ | htcondor ⏳");
                DashboardServer.updateStatus("htcondorStatus","Its working");
                createNodeAgents(); // ← создаём агентов для каждого узла
            }
        });



// Добавьте поведение для обработки команд извне

    }
    private void handlePlaybookFailure(String playbook, AnsibleRunner.AnsibleResult result) {
        String alert = "Playbook " + playbook + " failed: " + result.errorCode;
        logger.error(alert + " | Details: " + result.details);

        switch (result.errorCode) {
            case "TIMEOUT":
                sendAlert("ALERT: TIMEOUT in " + playbook + ". Check network or increase timeout.");
                break;
            case "CONNECTION_FAILURE":
                sendAlert("ALERT: UNREACHABLE NODE during " + playbook + ". Verify inventory and SSH keys.");
                break;
            default:
                sendAlert("ALERT: Execution failed in " + playbook + ": " + result.errorCode);
        }

        // Автоматически запускаем сбор логов при критической ошибке
        collectDiagnosticLogs();
    }

    private void createNodeAgents() {
        try {
            // Парсим inventory, используя this.inventory
            InventoryParser.Inventory inv = InventoryParser.parse(this.inventory);

            // Создаём агента для master-узлов
            for (InventoryParser.Host master : inv.getGroup("master")) {
                createNodeAgent(master.name, true);
            }

            // Создаём агентов для worker-узлов
            for (InventoryParser.Host worker : inv.getGroup("workers")) {
                createNodeAgent(worker.name, false);
            }

        } catch (Exception e) {
            logger.error("Failed to create node agents from inventory", e);
        }
    }

    private void createNodeAgent(String nodeName, boolean isMaster) {
        try {
            String agentType =  MasterAgent.class.getName();

            // Передаём аргументы агенту
            Object[] agentArgs = new Object[]{
                    nodeName,           // имя узла
                    this.inventory,     // путь к inventory
                    this.playbooksDir   // директория с плейбуками
            };

            AgentController ac = getContainerController().createNewAgent(
                    nodeName,
                    agentType,
                    agentArgs
            );
            ac.start();

            // Сохраняем AID для отправки сообщений
            AID agentAID = new AID(nodeName, AID.ISLOCALNAME);
            nodeAgents.put(nodeName, agentAID);

            logger.info("Created {} agent: {}", isMaster ? "master" : "worker", nodeName);
        } catch (Exception e) {
            logger.error("Failed to create agent for node: " + nodeName, e);
        }
    }

    private void handleNodeMessage(ACLMessage msg) {
        String sender = msg.getSender().getLocalName();
        String content = msg.getContent();

        if (content.startsWith("ALERT:")) {
            System.out.println("Coordinator: Received alert from " + sender + ": " + content);
            // Реакция на сбой: перезапуск, уведомление и т.д.
        } else if (content.equals("READY")) {
            System.out.println("Coordinator: Node " + sender + " is ready");
        }
    }

    private void sendAlert(String message) {
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.addReceiver(new AID("coordinator", AID.ISLOCALNAME));
        msg.setContent(message);
        send(msg);
    }
    private void collectDiagnosticLogs() {
        try {
            // Собираем логи Ansible последнего запуска
            String ansibleLog = "Last Ansible output not available"; // можно сохранять в файл из AnsibleRunner

            // Собираем системные логи с master-узла
            Process p1 = new ProcessBuilder("journalctl", "-u", "kubelet", "--since", "1 hour ago", "-n", "50").start();
            String kubeletLog = readOutput(p1);

            Process p2 = new ProcessBuilder("systemctl", "status", "containerd").start();
            String containerdLog = readOutput(p2);

            String fullLog = "=== ANSIBLE ===\n" + ansibleLog +
                    "\n=== KUBELET ===\n" + kubeletLog +
                    "\n=== CONTAINERD ===\n" + containerdLog;

            // Сохраняем в файл и обновляем статус
            Files.write(Paths.get("diagnostic-logs.txt"), fullLog.getBytes());
            DashboardServer.updateStatus("diagnosticLogs", "diagnostic-logs.txt");
            logger.info("Diagnostic logs collected to diagnostic-logs.txt");

        } catch (Exception e) {
            logger.error("Failed to collect diagnostic logs", e);
        }
    }

    private String readOutput(Process process) throws Exception {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            return r.lines().collect(Collectors.joining("\n"));
        }
    }
}
