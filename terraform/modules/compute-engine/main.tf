# =============================================================================
# Compute Engine Module
# =============================================================================
# Deploys a cost-optimized VM for Spring Boot API (hackathon-ready)
# Budget target: < $10/day with e2-medium
# =============================================================================

variable "project_id" {
  description = "GCP Project ID"
  type        = string
}

variable "region" {
  description = "GCP Region"
  type        = string
}

variable "zone" {
  description = "GCP Zone"
  type        = string
}

variable "labels" {
  description = "Labels to apply to resources"
  type        = map(string)
  default     = {}
}

variable "instance_name" {
  description = "Name of the VM instance"
  type        = string
  default     = "connectable-api"
}

variable "machine_type" {
  description = "Machine type (e2-medium recommended for hackathon)"
  type        = string
  default     = "e2-medium" # 2 vCPU, 4GB RAM, ~$0.034/hr = ~$0.82/day
}

variable "service_account_email" {
  description = "Service account email for the VM"
  type        = string
}

variable "network" {
  description = "VPC network name"
  type        = string
  default     = "default"
}

variable "subnetwork" {
  description = "Subnetwork name (optional)"
  type        = string
  default     = ""
}

variable "container_image" {
  description = "Docker image to run"
  type        = string
}

variable "env_vars" {
  description = "Environment variables for the container"
  type        = map(string)
  default     = {}
}

variable "secret_env_vars" {
  description = "Environment variables sourced from Secret Manager"
  type        = map(string)
  default     = {}
}

variable "worker_url" {
  description = "Cloud Run Worker URL"
  type        = string
}

# -----------------------------------------------------------------------------
# Container-Optimized OS VM
# -----------------------------------------------------------------------------
# IMPORTANT: Container-Optimized OS (COS) has specific constraints:
# - No gcloud CLI available
# - /root is read-only
# - Most filesystems are noexec
# - Must use /bin/bash to run scripts from /var/lib
# - Must use curl + Secret Manager REST API for secrets
# -----------------------------------------------------------------------------

locals {
  # Build environment variable flags for docker run (non-secret vars only)
  env_flags_simple = join(" ", concat(
    [for k, v in var.env_vars : "-e ${k}=\"${v}\""],
    ["-e WORKER_URL=\"${var.worker_url}\""]
  ))

  # Secret names for the start script to fetch
  secret_names = join(" ", [for k, secret_name in var.secret_env_vars : "${k}:connectable-${secret_name}"])

  # Cloud-init config to run container on startup
  cloud_config = <<-EOF
    #cloud-config
    
    write_files:
    # Script to pull Docker image with authentication
    - path: /var/lib/pull-image.sh
      permissions: '0644'
      content: |
        #!/bin/bash
        set -e
        mkdir -p /home/chronos/.docker
        export DOCKER_CONFIG=/home/chronos/.docker
        TOKEN=$(curl -s -H "Metadata-Flavor: Google" http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/token | grep -o '"access_token":"[^"]*' | cut -d'"' -f4)
        echo $TOKEN | docker login -u oauth2accesstoken --password-stdin ${var.region}-docker.pkg.dev
        docker pull ${var.container_image}
    
    # Script to start container with secrets from Secret Manager
    - path: /var/lib/start-connectable.sh
      permissions: '0644'
      content: |
        #!/bin/bash
        set -e
        
        # Get access token for Secret Manager API
        TOKEN=$(curl -s -H "Metadata-Flavor: Google" http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/token | grep -o '"access_token":"[^"]*' | cut -d'"' -f4)
        
        # Function to fetch secret from Secret Manager REST API
        get_secret() {
          curl -s -H "Authorization: Bearer $TOKEN" \
            "https://secretmanager.googleapis.com/v1/projects/${var.project_id}/secrets/$1/versions/latest:access" \
            | grep -o '"data": *"[^"]*' | cut -d'"' -f4 | base64 -d
        }
        
        # Fetch all secrets
        %{for k, secret_name in var.secret_env_vars~}
        ${k}=$(get_secret "connectable-${secret_name}")
        %{endfor~}
        
        # Run container with environment variables
        exec docker run --rm --name connectable-api \
          -p 8080:8080 \
          ${local.env_flags_simple} \
          %{for k, secret_name in var.secret_env_vars~}
          -e ${k}="$${k}" \
          %{endfor~}
          ${var.container_image}
    
    # Systemd service file
    - path: /etc/systemd/system/connectable-api.service
      permissions: '0644'
      content: |
        [Unit]
        Description=Connectable API Container
        After=docker.service
        Requires=docker.service
        
        [Service]
        Type=simple
        Restart=always
        RestartSec=10
        Environment="DOCKER_CONFIG=/home/chronos/.docker"
        ExecStartPre=/bin/bash /var/lib/pull-image.sh
        ExecStart=/bin/bash /var/lib/start-connectable.sh
        ExecStop=/usr/bin/docker stop connectable-api
        
        [Install]
        WantedBy=multi-user.target
    
    runcmd:
    - mkdir -p /home/chronos/.docker
    - systemctl daemon-reload
    - systemctl enable connectable-api.service
    - systemctl start connectable-api.service
  EOF
}

resource "google_compute_instance" "api" {
  name         = var.instance_name
  machine_type = var.machine_type
  zone         = var.zone
  project      = var.project_id

  # Use Container-Optimized OS for easy Docker deployment
  boot_disk {
    initialize_params {
      image = "projects/cos-cloud/global/images/family/cos-stable"
      size  = 10            # 10GB, minimum for COS
      type  = "pd-standard" # Standard persistent disk (cheaper than SSD)
    }
  }

  network_interface {
    network    = var.network
    subnetwork = var.subnetwork != "" ? var.subnetwork : null

    # Assign external IP for public access
    access_config {
      # Ephemeral IP (free, unlike static IP)
    }
  }

  # Container configuration via metadata
  metadata = {
    user-data                 = local.cloud_config
    google-logging-enabled    = "true"
    google-monitoring-enabled = "true"
  }

  # Service account with required permissions
  service_account {
    email  = var.service_account_email
    scopes = ["cloud-platform"] # Full access, controlled by IAM
  }

  # Allow HTTP/HTTPS traffic
  tags = ["http-server", "https-server"]

  labels = var.labels

  # Allow stopping for updates
  allow_stopping_for_update = true

  # Scheduling options - standard (not preemptible for hackathon stability)
  scheduling {
    preemptible       = false
    automatic_restart = true
  }

  lifecycle {
    ignore_changes = [
      metadata["ssh-keys"],
    ]
  }
}

# -----------------------------------------------------------------------------
# Firewall Rules
# -----------------------------------------------------------------------------

resource "google_compute_firewall" "allow_http" {
  name    = "${var.instance_name}-allow-http"
  network = var.network
  project = var.project_id

  allow {
    protocol = "tcp"
    ports    = ["8080"]
  }

  source_ranges = ["0.0.0.0/0"]
  target_tags   = ["http-server"]
}

# -----------------------------------------------------------------------------
# Static IP (Optional - commented out to save costs)
# Uncomment if you need a stable IP for DNS
# -----------------------------------------------------------------------------

# resource "google_compute_address" "api" {
#   name    = "${var.instance_name}-ip"
#   region  = var.region
#   project = var.project_id
# }

# -----------------------------------------------------------------------------
# Outputs
# -----------------------------------------------------------------------------

output "instance_name" {
  description = "Name of the VM instance"
  value       = google_compute_instance.api.name
}

output "instance_id" {
  description = "ID of the VM instance"
  value       = google_compute_instance.api.instance_id
}

output "external_ip" {
  description = "External IP address of the VM"
  value       = google_compute_instance.api.network_interface[0].access_config[0].nat_ip
}

output "internal_ip" {
  description = "Internal IP address of the VM"
  value       = google_compute_instance.api.network_interface[0].network_ip
}

output "api_url" {
  description = "URL to access the API"
  value       = "http://${google_compute_instance.api.network_interface[0].access_config[0].nat_ip}:8080"
}

output "ssh_command" {
  description = "SSH command to connect to the VM"
  value       = "gcloud compute ssh ${google_compute_instance.api.name} --zone=${var.zone} --project=${var.project_id}"
}
