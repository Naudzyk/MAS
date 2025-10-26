function updateDashboard() {
    fetch('/api/status')
        .then(response => response.json())
        .then(data => {
            // Используем правильные ID элементов из HTML
            const ansibleStageEl = document.getElementById('ansible-stage');
            const clusterStatusEl = document.getElementById('cluster-status');
            const alertsDiv = document.getElementById('alerts');
            
            if (ansibleStageEl) {
                ansibleStageEl.textContent = data.ansibleStage || '—';
            }
            
            if (clusterStatusEl) {
                clusterStatusEl.textContent = data.htcondorStatus || '—';
            }
            
            if (alertsDiv) {
                if (data.alerts && data.alerts.length > 0) {
                    alertsDiv.innerHTML = data.alerts.map(a =>
                        `<div class="status-item status-error">${a}</div>`
                    ).join('');
                } else {
                    alertsDiv.innerHTML = 'Нет оповещений';
                }
            }
        })
        .catch(err => console.error('Ошибка загрузки данных:', err));
}

// Обновляем каждые 2 секунды
setInterval(updateDashboard, 2000);
updateDashboard(); // сразу при загрузке