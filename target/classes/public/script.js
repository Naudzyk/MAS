function updateDashboard() {
    fetch('/api/status')
        .then(response => response.json())
        .then(data => {
            document.getElementById('ansible-status').textContent = data.ansibleStage || '—';
            document.getElementById('htcondor-status').textContent = data.htcondorStatus || '—';

            const alertsList = document.getElementById('alerts-list');
            alertsList.innerHTML = '';
            if (data.alerts && data.alerts.length > 0) {
                data.alerts.forEach(alert => {
                    const li = document.createElement('li');
                    li.textContent = alert;
                    alertsList.appendChild(li);
                });
            }

            const time = new Date(data.lastUpdate);
            document.getElementById('last-update').textContent = time.toLocaleString();
        })
        .catch(err => console.error('Ошибка загрузки данных:', err));
}

// Обновляем каждые 2 секунды
setInterval(updateDashboard, 2000);
updateDashboard(); // сразу при загрузке