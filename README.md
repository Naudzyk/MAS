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

[masters]
master1 ansible_host=192.168.56.107 ansible_user=vboxuser ansible_ssh_private_key_file=~/.ssh/id_ed25519

[workers]
worker1 ansible_host=192.168.56.105 ansible_user=vboxuser ansible_ssh_private_key_file=~/.ssh/id_ed25519
```

### Запуск развертывания
1. Заполните `scripts/inventory.ini`.
2. Убедитесь, что плейбуки лежат в `scripts/`.
3. Запустите приложение:
   -`java -jar target/MAS-1.0-SNAPSHOT.jar`
4. Приложение автоматически запускает `BootstrapAgent` для узлов из группы `[bootstrap]`.
5. После успешного bootstrap автоматически стартует `CoordinatorAgent`, который запускает плейбуки по этапам.

Если группа `[bootstrap]` отсутствует или пуста, координатор стартует сразу, без первичной настройки.

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


