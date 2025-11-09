[central_manager]
%{ for ip in central_manager }${ip}
%{ endfor }

[execute_nodes]
%{ for ip in execute_nodes }${ip}
%{ endfor }

