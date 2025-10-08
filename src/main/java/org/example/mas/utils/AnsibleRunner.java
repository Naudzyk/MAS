package org.example.mas.utils;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class AnsibleRunner {
    private static final Logger logger = LoggerFactory.getLogger(AnsibleRunner.class);

    /**
     * Запускает Ansible-плейбук и ждёт завершения.
     *
     * @param playbook      имя файла плейбука (например, "03_kubernetes_install.yml")
     * @param inventoryPath путь к inventory-файлу
     * @param workingDir    директория с плейбуками
     * @param timeoutMinutes таймаут в минутах
     * @return true, если успешно
     */
    public static boolean run(String playbook, String inventoryPath, String workingDir, int timeoutMinutes) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "ansible-playbook", "-i", inventoryPath, playbook
            );
            pb.directory(new File(workingDir));
            pb.environment().put("ANSIBLE_HOST_KEY_CHECKING", "False");
            pb.redirectErrorStream(true);

            Process process = pb.start();

            // Асинхронное чтение вывода
            CompletableFuture<String> output = CompletableFuture.supplyAsync(() -> {
                StringBuilder sb = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        logger.info("ANSIBLE | {}", line);
                        sb.append(line).append("\n");
                    }
                } catch (Exception e) {
                    logger.error("Error reading Ansible output", e);
                }
                return sb.toString();
            });

            boolean finished = process.waitFor(timeoutMinutes, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                logger.warn("Ansible playbook {} timed out after {} minutes", playbook, timeoutMinutes);
                return false;
            }

            int exitCode = process.exitValue();
            boolean success = exitCode == 0;
            logger.info("Ansible playbook {} completed with exit code: {}", playbook, exitCode);
            return success;

        } catch (Exception e) {
            logger.error("Failed to run Ansible playbook: " + playbook, e);
            return false;
        }
    }
}