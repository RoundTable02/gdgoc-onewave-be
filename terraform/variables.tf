# =============================================================================
# Project Variables
# =============================================================================

variable "project_id" {
  description = "GCP Project ID"
  type        = string
}

variable "region" {
  description = "GCP Region for resources"
  type        = string
  default     = "asia-northeast3"
}

variable "zone" {
  description = "GCP Zone for Compute Engine"
  type        = string
  default     = "asia-northeast3-a"
}

# =============================================================================
# Compute Engine Variables
# =============================================================================

variable "instance_name" {
  description = "Name of the Compute Engine instance"
  type        = string
  default     = "connectable-api"
}

variable "machine_type" {
  description = "Machine type for VM (e2-medium: 2 vCPU, 4GB RAM, ~$0.82/day)"
  type        = string
  default     = "e2-medium"
}

variable "container_image" {
  description = "Spring Boot API Docker image"
  type        = string
}

# =============================================================================
# GCS Variables
# =============================================================================

variable "gcs_bucket_name" {
  description = "Name of the GCS bucket for submissions (must be globally unique)"
  type        = string
}

variable "gcs_storage_class" {
  description = "Storage class for the GCS bucket"
  type        = string
  default     = "STANDARD"

  validation {
    condition     = contains(["STANDARD", "NEARLINE", "COLDLINE", "ARCHIVE"], var.gcs_storage_class)
    error_message = "Storage class must be one of: STANDARD, NEARLINE, COLDLINE, ARCHIVE."
  }
}

variable "gcs_lifecycle_age_days" {
  description = "Number of days after which objects are deleted (0 = disabled)"
  type        = number
  default     = 7 # Short for hackathon
}

# =============================================================================
# Database (Supabase) Variables
# =============================================================================

variable "supabase_host" {
  description = "Supabase PostgreSQL host"
  type        = string
  sensitive   = true
}

variable "supabase_db" {
  description = "Supabase database name"
  type        = string
  default     = "postgres"
}

variable "supabase_user" {
  description = "Supabase database user"
  type        = string
  default     = "postgres"
}

variable "supabase_password" {
  description = "Supabase database password"
  type        = string
  sensitive   = true
}

# =============================================================================
# External Services Variables
# =============================================================================

variable "gemini_api_key" {
  description = "Google Gemini API key for AI script generation"
  type        = string
  sensitive   = true
}

variable "gemini_model" {
  description = "Gemini model to use"
  type        = string
  default     = "gemini-2.5-pro"
}

variable "worker_timeout_seconds" {
  description = "Timeout for worker API calls (from API to Worker)"
  type        = number
  default     = 60
}

variable "worker_image" {
  description = "Cloud Run Worker Docker image (NestJS Playwright)"
  type        = string
}

variable "worker_min_instances" {
  description = "Worker minimum instances (0 for cost optimization)"
  type        = number
  default     = 0
}

variable "worker_max_instances" {
  description = "Worker maximum instances"
  type        = number
  default     = 5
}

# =============================================================================
# CORS Configuration
# =============================================================================

variable "cors_allowed_origins" {
  description = "List of allowed CORS origins"
  type        = list(string)
  default     = ["*"] # Open for hackathon
}

# =============================================================================
# Labels
# =============================================================================

variable "labels" {
  description = "Common labels to apply to all resources"
  type        = map(string)
  default     = {}
}
