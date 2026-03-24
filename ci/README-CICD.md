# CI/CD: пайплайн развёртывания HTCondor и запуска заданий

Кратко: как устроен пайплайн, какие переменные задать в GitLab и как его дорабатывать самому.

---

## Что делает пайплайн

1. **setup-nodes** — проверяет доступность узлов (ping + SSH). Нужны: submit-нода и при необходимости execute-ноды.
2. **deploy-cluster** — скачивает MAS из GitLab Package Registry, копирует на submit-ноду, запускает MAS (Java), ждёт перехода кластера в статус READY через REST API (`GET /api/status` → `clusterStatus = "DEPLOY CLUSTER"`).
3. **run-job** — с runner **kubectl cp** копирует пакет задания **напрямую в под** htcondor-manager (без узла-посредника), затем kubectl exec (venv, condor_submit), ожидание по condor_q. Нужен **KUBECONFIG** на runner (или KUBE_CONFIG_B64).
4. **validate** — с runner **kubectl cp** скачивает результаты **напрямую из пода** в ci/artifacts. Проверка выходных файлов.

Узлы не создаются пайплайном — они уже выделены; пайплайн только проверяет доступ и разворачивает на них ПО и задание.

---

## Где что лежит

- **`.gitlab-ci.yml`** (в корне репо) — описание пайплайна: stages, jobs, образы, переменные по умолчанию.
- **`ci/`** — скрипты, которые вызываются из jobs:
  - `setup_nodes.sh` — проверка узлов;
  - `deploy_cluster.sh` — загрузка MAS, копирование, запуск, ожидание READY;
  - `run_job.sh` — копирование пакета в под htcondor-manager (kubectl), condor_submit в поде, ожидание завершения;
  - `validate_results.sh` — скачивание результатов из пода (kubectl cp) и проверка.
- Пакет задания — папка в репо (например `8ecef58c-b8ca-461a-acd9-20c43e9fca5f`), путь задаётся переменной **`JOB_PACKAGE_PATH`**.

Пайплайн рассчитан на **self-hosted runner с тегом `manager`**, у которого есть SSH-доступ ко всем нужным узлам.

---

## Переменные GitLab CI/CD (Settings → CI/CD → Variables)

Обязательные (без них пайплайн падает или ведёт себя неправильно):

| Переменная | Описание | Protected / Masked |
|------------|----------|--------------------|
| `SSH_PRIVATE_KEY` | Приватный SSH-ключ для доступа к submit/execute узлам | Да / Да |
| `SSH_USER` | Пользователь для SSH (например `ubuntu`) | По желанию |
| `SUBMIT_NODE` | IP или hostname submit-ноды (где крутится MAS и condor_submit) | Да |
| `EXECUTE_NODES` | Список execute-нод через запятую (для проверки в setup-nodes) | По желанию |
| `CONDOR_CLUSTER_PASSWORD` | Пароль пула HTCondor (передаётся в MAS) | Да / Да |
| `MAS_PACKAGE_URL` | URL архива MAS. Если не задан и пайплайн идёт в GitLab CI — берётся пакет из реестра **текущего проекта**: `mas/1.0/mas-deploy-v1.0.zip` (см. опциональные `MAS_PACKAGE_*`) | По желанию (в CI — авто) |
| `KUBECONFIG` или `KUBE_CONFIG_B64` | Доступ к кластеру K8s с **runner** (для run-job и validate): путь к kubeconfig (например `/etc/kubernetes/admin.conf` если runner — мастер-узел) или base64 содержимого файла | Да (для run/validate) |

Опциональные:

| Переменная | Описание | По умолчанию |
|------------|----------|--------------|
| `KUBE_NAMESPACE` | Namespace, где крутится под htcondor-manager | `default` |
| `HTCONDOR_MANAGER_POD` | Точное имя пода (если известно). Если не задано — ищется по префиксу | — |
| `HTCONDOR_MANAGER_POD_PREFIX` | Префикс имени пода для поиска (`kubectl get pods \| grep ^префикс`) | `htcondor-manager` |
| `MAS_PACKAGE_NAME` | Имя пакета в Package Registry (для авто-URL) | `mas` |
| `MAS_PACKAGE_VERSION` | Версия пакета в Package Registry | `1.0` |
| `MAS_PACKAGE_FILE` | Имя файла в Package Registry (например `mas-deploy-v1.0.zip`) | `mas-deploy-v1.0.zip` |
| `REMOTE_JOB_DIR` | Каталог **в поде** для пакета задания и результатов | `/tmp/condor-job` |
| `PYTHON_VENV_NAME` | Имя каталога виртуального окружения Python **внутри** REMOTE_JOB_DIR (создаётся перед запуском задания; туда ставится SALib и др.) | `python_env` |
| `PIP_PACKAGES` | Доп. пакеты для `pip install` в venv (через пробел). Если в пакете задания нет `requirements.txt`, задайте здесь, например `salib`. | — |

Если runner запущен на мастер-узле кластера, задайте **KUBECONFIG** = `/etc/kubernetes/admin.conf`. Либо используйте **KUBE_CONFIG_B64** (base64 содержимого kubeconfig).

| `INVENTORY_CONTENT` | Полное содержимое `inventory.ini` для Ansible (группы `[central_manager]`, `[execute_nodes]` с `ansible_host`, `ansible_user`, ключом). Если **не задано** — inventory собирается автоматически из **SUBMIT_NODE** и **EXECUTE_NODES** (те же IP), ключ копируется на submit-ноду в `scripts/.ssh_deploy_key` | — |
| `JOB_PACKAGE_PATH` | Путь к папке с заданием в репо | `8ecef58c-b8ca-461a-acd9-20c43e9fca5f` |
| `JOB_SUBMIT_FILE` | Файл для отправки: `salib.dag` (DAG) или `run.sub` (один job) | `salib.dag` |
| `MAS_READY_TIMEOUT` | Таймаут ожидания READY (сек) | `300` |
| `JOB_WAIT_TIMEOUT` | Таймаут ожидания задания (сек) | `3600` |
| `NODE_CHECK_TIMEOUT` | Таймаут проверки узла (сек) | `60` |
| `MAS_REMOTE_DIR` | Каталог на submit-ноде для MAS | `/opt/mas` |
| `JOB_OUTPUT_FILES` | Список файлов для валидации (пробелы) | `analyze.csv model_output.csv ...` |

### Inventory для Ansible (IP узлов)

Ansible-плейбуки MAS ожидают `scripts/inventory.ini` с группами **`[central_manager]`** (submit-нода) и **`[execute_nodes]`** (worker-ноды). Каждый хост задаётся с `ansible_host=<IP>`, `ansible_user=...`, `ansible_ssh_private_key_file=...`.

- **Если задан `INVENTORY_CONTENT`** — он целиком записывается в `inventory.ini`; ключ на submit-ноду не копируется (должен быть там заранее).
- **Если не задан** — скрипт **деploy_cluster.sh** сам формирует inventory из **SUBMIT_NODE** и **EXECUTE_NODES** (те же IP, что и для проверки в setup-nodes) и кладёт ключ в `scripts/.ssh_deploy_key` на submit-ноде, чтобы MAS (Ansible) мог подключаться к central_manager и worker’ам.

Итого: достаточно задать **SUBMIT_NODE** и **EXECUTE_NODES** (IP через запятую) — inventory и ключ для Ansible подставятся автоматически.

---

## Как пайплайн устроен технически

- **Stages** идут строго по порядку: `setup-nodes` → `deploy-cluster` → `run-job` → `validate`. При падении одного job следующие не запускаются.
- У каждого job заданы **tags: [manager]** и **timeout: 1h**, то есть пайплайн выполняется только на твоём runner с тегом `manager`.
- В **variables** в `.gitlab-ci.yml` заданы пути и таймауты по умолчанию; их можно переопределить через CI/CD Variables (или в правиле `variables:` у job).
- Скрипты в **`ci/`** вызываются из **script:** соответствующего job. Они получают переменные окружения из GitLab (в т.ч. секреты). Логика: bash, `set -euo pipefail`, таймауты и повторные проверки там, где нужно (например, опрос `/api/status` и `condor_q`).

Ожидание READY:

- В **deploy_cluster.sh** в цикле вызывается `GET http://${SUBMIT_NODE}:8080/api/status`. В ответе проверяется поле `clusterStatus == "DEPLOY CLUSTER"` (это и есть READY). Интервал и таймаут задаются переменными `MAS_POLL_INTERVAL` и `MAS_READY_TIMEOUT`.

Ожидание завершения задания:

- В **run_job.sh** пакет задания пакуется в tar, копируется в под **htcondor-manager** через `kubectl cp`, в поде распаковывается. Затем в поде выполняется `condor_submit` / `condor_submit_dag`. Cluster id извлекается из вывода; в цикле выполняется `kubectl exec ... condor_q <cluster_id>`; как только очередь пуста, задание считается завершённым. Таймаут — `JOB_WAIT_TIMEOUT`.

---

## Как дорабатывать пайплайн самому

1. **Добавить/изменить шаги**
   - В `.gitlab-ci.yml`: новый **stage** (например `notify`) и новый **job** с `extends: .only_manager_runner`, своим `script:` и при необходимости `before_script:`.
   - Если логика сложная — вынести её в отдельный скрипт в `ci/` (например `ci/notify.sh`) и в `script:` вызывать только его.

2. **Изменить проверки узлов**
   - Редактировать `ci/setup_nodes.sh`: добавить проверки (например, порты, наличие condor), оставив использование `SSH_PRIVATE_KEY`, `SUBMIT_NODE`, `EXECUTE_NODES`, `SSH_USER`.

3. **Изменить способ ожидания READY**
   - В `ci/deploy_cluster.sh`: поменять URL (если MAS на другом порту), условие по полю статуса или формат ответа (если добавите новый эндпоинт).

4. **Другой способ отправки задания**
   - В `ci/run_job.sh`: заменить вызов `condor_submit`/`condor_submit_dag` на свою команду или добавить ветку по переменной (например, по `JOB_SUBMIT_FILE` или новой переменной).

5. **Валидация результатов**
   - В `ci/validate_results.sh`: изменить список файлов (`JOB_OUTPUT_FILES`), добавить проверки содержимого (например, DuckDB/CSV), писать артефакты в `ci/artifacts/` — они уже сохраняются в job **validate** через **artifacts: paths**.

6. **Использовать другой образ**
   - В нужном job заменить `image: alpine:latest` на свой образ (например, с установленным `rsync`, `condor_client` и т.д.) и при необходимости обновить `before_script:` (установка пакетов).

7. **Запускать только часть пайплайна**
   - В **rules:** оставить `if: $CI_COMMIT_BRANCH` и при необходимости добавить условия по ветке или по переменной (например, `if: $RUN_FULL_PIPELINE == "true"`). Можно завести несколько job в одном stage с разными **rules:**.

8. **MAS из другого источника**
   - Поменять в `ci/deploy_cluster.sh` способ загрузки: подставить другой URL в `MAS_PACKAGE_URL` или использовать `curl` с токеном/заголовками для своего артефакт-хранилища (например, другой GitLab-проект или Generic Package).

---

## Публикация MAS в GitLab Package Registry

Чтобы пайплайн мог скачать MAS:

1. Собери дистрибутив: `mvn package -Pdist`, создай архив (например `mas-deploy-v1.0.zip` из `target/mas-deploy/`).
2. В проекте GitLab: **Deploy → Package Registry → Generic packages** (или **Packages & registries**).
3. Загрузи пакет через API или вручную; получится URL вида:
   `https://<gitlab>/api/v4/projects/<id>/packages/generic/<name>/<version>/<file>`
4. **Вариант А:** Ничего не задавать — в GitLab CI скрипт сам подставит URL пакета **текущего проекта**: `mas/1.0/mas-deploy-v1.0.zip`. Если в реестре лежит именно этот файл, переменная **MAS_PACKAGE_URL** не нужна. При другом имени/версии задай опциональные переменные: `MAS_PACKAGE_NAME`, `MAS_PACKAGE_VERSION`, `MAS_PACKAGE_FILE`.
5. **Вариант Б:** В CI/CD Variables задай **MAS_PACKAGE_URL** полным URL архива. Для доступа из другого проекта используй **CI_JOB_TOKEN** или **Deploy token** с правами на чтение пакетов; в `deploy_cluster.sh` уже предусмотрен заголовок `JOB-TOKEN` при наличии `CI_JOB_TOKEN`.

---

## REST API MAS для пайплайна

- **GET /api/status** — возвращает JSON с полем `clusterStatus`. Значение `"DEPLOY CLUSTER"` означает, что кластер развёрнут и готов (READY). Эндпоинт добавлен в MAS для опроса из CI/CD.

Если захочешь другой критерий READY (например, отдельный эндпоинт или поле), нужно будет поправить проверку в `ci/deploy_cluster.sh` под новый формат ответа.
