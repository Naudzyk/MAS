%{ for group, nodes in groups }
[${group}]
%{ for node in nodes }
${node.inventory_name} ansible_host=${node.ip_address} ansible_user=${node.ssh_user}%{ if node.ssh_key_path != "" ~} ansible_ssh_private_key_file=${node.ssh_key_path}%{ endif }
%{ endfor }
%{ endfor }