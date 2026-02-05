### HTCondor Multi‑Agent Deployment System (JADE + Ansible)

Система оркестрации, которая поэтапно разворачивает кластер Kubernetes и HTCondor с помощью агентов JADE, запускающих Ansible‑плейбуки. Порядок этапов строго последовательный и контролируется координатором.

### Что делает
- Этапы: системная подготовка → containerd → установка Kubernetes → инициализация Kubernetes → Calico CNI → подготовка воркеров → присоединение воркеров → HTCondor.
- Каждый этап исполняет отдельный агент. `CoordinatorAgent` шлёт `START_*`, агент запускает один плейбук и отвечает `COMPLETE`/`FAILED`.

### Требования
- Машина управления (Linux):
  - Java 17+ (OpenJDK)
  - Ansible 2.13+ (Python 3)
  - SSH‑доступ к целевым хостам
- Целевые узлы (manager и worker): Debian/Ubuntu с systemd.

### Структура
- `target/MAS-1.0-SNAPSHOT.jar` — собранное приложение (имя может отличаться)
- `scripts/inventory.ini` — Ansible‑инвентарь
- `scripts/` — каталог с плейбуками:
  - `01_system_preparation.yml`
  - `02_containerd.yml`
  - `03_kubernetes_install.yml`
  - `04_kubernetes_init.yml`
  - `05_calico_cni.yml`
  - `06_worker_preparation.yml`
  - `07_worker_join.yml`
  - `08_htcondor.yml`

### Конфигурация (application.yml)
Минимальные настройки:
```yaml
mas:
  mode: default            # default | existing-cluster
  paths:
    inventory: scripts/inventory.ini
    playbooks: scripts
  bootstrap:
    group: bootstrap
    public-key: ~/.ssh/id_ed25519.pub
```

Дополнительно можно управлять последовательностью плейбуков:
```yaml
mas:
  playbooks:
    skip: 02_containerd.yml,03_kubernetes_install.yml
    # sequence: 08_htcondor.yml,09_prometheus.yml,10_prometheus_server.yml
```

### Роли групп inventory
- `[bootstrap]` — узлы, куда нужно зайти по паролю и настроить SSH‑ключ + sudo.
- `[central_manager]` — управляющий узел кластера (master/manager).
- `[execute_nodes]` — рабочие узлы (workers).

### Настройка inventory.ini
Bootstrap‑учётные данные читаются из группы `[bootstrap]`. Для каждого узла нужны:
- `ansible_host` — IP хоста
- `ansible_user` — пользователь для SSH
- `bootstrap_password` — пароль пользователя (используется только на этапе bootstrap)
- `bootstrap_public_key` — путь до публичного ключа (опционально, можно задать один раз)

Пример:
```ini
[bootstrap]
node1 ansible_host=192.168.56.107 ansible_user=vboxuser bootstrap_password=secret bootstrap_public_key=~/.ssh/id_ed25519.pub

[central_manager]
manager ansible_host=192.168.56.107 ansible_user=vboxuser ansible_ssh_private_key_file=~/.ssh/id_ed25519

[execute_nodes]
worker ansible_host=192.168.56.105 ansible_user=vboxuser ansible_ssh_private_key_file=~/.ssh/id_ed25519
```

### Запуск развертывания
1. Заполните `scripts/inventory.ini`.
2. Убедитесь, что плейбуки лежат в `scripts/`.
3. Запустите приложение:
   - `java -jar target/MAS-1.0-SNAPSHOT.jar`
4. Приложение автоматически запускает `BootstrapAgent` для узлов из группы `[bootstrap]`.
5. После успешного bootstrap автоматически стартует `CoordinatorAgent`, который запускает плейбуки по этапам.

Если группа `[bootstrap]` отсутствует или пуста, координатор стартует сразу, без первичной настройки.

### Запуск на существующем Kubernetes‑кластере
Самый простой способ — включить режим существующего кластера:

В `application.yml`:
```yaml
mas:
  mode: existing-cluster
```

Или через переменную окружения:
```
MAS_MODE=existing-cluster
```

Это автоматически пропустит плейбуки установки Kubernetes и containerd (этап `01_system_preparation.yml` остаётся).

Когда использовать `existing-cluster`:
- Kubernetes уже установлен и работает.
- containerd настроен и запущен.
- Нужна только установка HTCondor/Prometheus поверх существующего кластера.

Что именно пропускается:
- `02_containerd.yml`
- `03_kubernetes_install.yml`
- `04_kubernetes_init.yml`
- `05_calico_cni.yml`
- `06_worker_preparation.yml`
- `07_worker_join.yml`

Также можно вручную задать список пропускаемых плейбуков:

В `src/main/resources/application.yml`:
```yaml
mas:
  playbooks:
    skip: 02_containerd.yml,03_kubernetes_install.yml,04_kubernetes_init.yml,05_calico_cni.yml,06_worker_preparation.yml,07_worker_join.yml
```

Или через переменную окружения:
```
MAS_PLAYBOOKS_SKIP=02_containerd.yml,03_kubernetes_install.yml,04_kubernetes_init.yml,05_calico_cni.yml,06_worker_preparation.yml,07_worker_join.yml
```

Можно также задать явную последовательность:
```
MAS_PLAYBOOKS_SEQUENCE=08_htcondor.yml,09_prometheus.yml,10_prometheus_server.yml
```

### Установка зависимостей (Ubuntu/Debian)
```bash
# Java 17
sudo apt update && sudo apt install -y openjdk-17-jdk

# Ansible
sudo apt install -y ansible

# Проверка
java -version
ansible --version
```

### Частые ошибки
- `System has not been booted with systemd as init system` — узел без systemd (контейнер/WSL). Включите `mas.mode=existing-cluster` или исключите `02–07` через `mas.playbooks.skip`.
- `Inventory не найден` — проверьте `mas.paths.inventory` и наличие `scripts/inventory.ini`.
- `Playbook ... failed: CONNECTION_FAILURE` — проверьте SSH‑доступ, пользователей и ключи.


