terraform {
  required_providers {
    null = {
          source   = "hashicorp/null"
          version  = "3.2.1"
        }
    local = {
        source = "hashicorp/local"
        version = "2.4.0"
        }
    template = {
        source = "hashicorp/template"
        version = "2.2.0"
        }
    }
  }


resource "null_resource" "existing_node" {
    triggers = {
        node_ip = var.node_ip
        }

    provisioner "remote-exec" {
        inline = ["echo 'Node is reachable!'"]

        connection {
            type        = "ssh"
            user        = var.ansible_user
            private_key = file(var.ssh_key_path)
            host        = var.node_ip
            }
        }
    }
data "template_file" "inventory" {
    template = file("${path.module}/templates/inventory.tpl")
    vars ={
        node_ip     = var.node_ip
        ansible_user= var.ansible_user
        ssh_key_path = var.ssh_key_path
        }
    }
resource "local_file" "inventory" {
    content  = data.template_file.inventory.rendered
    filename ="${path.root}/../inventory.ini"
    }