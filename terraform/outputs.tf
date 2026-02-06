# =============================================================================
# Outputs
# =============================================================================

# -----------------------------------------------------------------------------
# Compute Engine Outputs
# -----------------------------------------------------------------------------

output "vm_external_ip" {
  description = "External IP address of the VM"
  value       = module.compute_engine.external_ip
}

output "vm_internal_ip" {
  description = "Internal IP address of the VM"
  value       = module.compute_engine.internal_ip
}

output "api_url" {
  description = "URL to access the Spring Boot API"
  value       = module.compute_engine.api_url
}

output "ssh_command" {
  description = "SSH command to connect to the VM"
  value       = module.compute_engine.ssh_command
}

# -----------------------------------------------------------------------------
# GCS Outputs
# -----------------------------------------------------------------------------

output "gcs_bucket_name" {
  description = "Name of the GCS bucket"
  value       = module.gcs.bucket_name
}

output "gcs_bucket_url" {
  description = "URL of the GCS bucket for static hosting"
  value       = module.gcs.bucket_url
}

# -----------------------------------------------------------------------------
# Artifact Registry Outputs
# -----------------------------------------------------------------------------

output "artifact_registry_url" {
  description = "URL of the Artifact Registry repository"
  value       = "${google_artifact_registry_repository.connectable.location}-docker.pkg.dev/${var.project_id}/${google_artifact_registry_repository.connectable.repository_id}"
}

# -----------------------------------------------------------------------------
# Service Account Outputs
# -----------------------------------------------------------------------------

output "service_account_email" {
  description = "Email of the VM service account"
  value       = module.iam.service_account_email
}

# -----------------------------------------------------------------------------
# Secret Manager Outputs
# -----------------------------------------------------------------------------

output "secret_ids" {
  description = "Map of secret names to their IDs"
  value       = module.secrets.secret_ids
  sensitive   = true
}

# -----------------------------------------------------------------------------
# Cloud Run Worker Outputs
# -----------------------------------------------------------------------------

output "worker_url" {
  description = "URL of the Cloud Run Worker"
  value       = module.cloud_run_worker.service_url
}

output "worker_service_name" {
  description = "Name of the Cloud Run Worker service"
  value       = module.cloud_run_worker.service_name
}

# -----------------------------------------------------------------------------
# Cost Estimate
# -----------------------------------------------------------------------------

output "estimated_daily_cost" {
  description = "Estimated daily cost"
  value       = "~$0.82/day (CE e2-medium) + ~$0-2/day (Cloud Run, usage-based)"
}
