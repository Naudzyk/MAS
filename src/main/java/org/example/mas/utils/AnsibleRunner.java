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
    public static AnsibleResult run(String playbook, String inventoryPath, String workingDir, int timeoutMinutes) {
        try {
            ProcessBuilder pb = new ProcessBuilder("ansible-playbook", "-i", inventoryPath, playbook);
            pb.directory(new File(workingDir));
            pb.environment().put("ANSIBLE_HOST_KEY_CHECKING", "False");
            pb.redirectErrorStream(true);

            Process process = pb.start();
            StringBuilder output = new StringBuilder();

            // Чтение вывода в реальном времени
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logger.info("ANSIBLE | {}", line);
                    output.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(timeoutMinutes, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                return new AnsibleResult(false, "TIMEOUT", "Playbook timed out after " + timeoutMinutes + " min");
            }

            int exitCode = process.exitValue();
            boolean success = exitCode == 0;

            // Анализ ошибки: сбой подключения?
            String outputStr = output.toString();
            if (!success) {
                if (outputStr.contains("UNREACHABLE!") || outputStr.contains("Failed to connect")) {
                    return new AnsibleResult(false, "CONNECTION_FAILURE", outputStr);
                }
                return new AnsibleResult(false, "EXECUTION_ERROR", outputStr);
            }

            return new AnsibleResult(true, "SUCCESS", outputStr);

        } catch (Exception e) {
            logger.error("Exception running playbook: " + playbook, e);
            return new AnsibleResult(false, "EXCEPTION", e.getMessage());

        }
    }

    // Вспомогательный класс для результата
    public static class AnsibleResult {
        public final boolean success;
        public final String errorCode; // TIMEOUT, CONNECTION_FAILURE, EXECUTION_ERROR, EXCEPTION
        public final String details;

        public AnsibleResult(boolean success, String errorCode, String details) {
            this.success = success;
            this.errorCode = errorCode;
            this.details = details;
        }
    }
}