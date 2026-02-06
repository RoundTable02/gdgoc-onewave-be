# =============================================================================
# GCS Module
# =============================================================================
# Creates a GCS bucket for static file hosting with public read access
# =============================================================================

variable "project_id" {
  description = "GCP Project ID"
  type        = string
}

variable "region" {
  description = "GCP Region"
  type        = string
}

variable "bucket_name" {
  description = "Name of the GCS bucket (must be globally unique)"
  type        = string
}

variable "storage_class" {
  description = "Storage class for the bucket"
  type        = string
  default     = "STANDARD"
}

variable "lifecycle_age_days" {
  description = "Number of days after which objects are deleted (0 = disabled)"
  type        = number
  default     = 90
}

variable "cors_allowed_origins" {
  description = "List of allowed CORS origins"
  type        = list(string)
  default     = ["*"]
}

variable "labels" {
  description = "Labels to apply to the bucket"
  type        = map(string)
  default     = {}
}

# -----------------------------------------------------------------------------
# GCS Bucket
# -----------------------------------------------------------------------------

resource "google_storage_bucket" "submissions" {
  name          = var.bucket_name
  location      = var.region
  project       = var.project_id
  storage_class = var.storage_class

  # Force destroy allows deleting bucket with objects
  # Set to false in production to prevent accidental data loss
  force_destroy = true

  # Use uniform bucket-level access (recommended)
  uniform_bucket_level_access = true

  # Static website hosting configuration
  website {
    main_page_suffix = "index.html"
    not_found_page   = "404.html"
  }

  # CORS configuration for frontend access
  cors {
    origin          = var.cors_allowed_origins
    method          = ["GET", "HEAD", "OPTIONS"]
    response_header = ["Content-Type", "Cache-Control"]
    max_age_seconds = 3600
  }

  # Lifecycle rule to delete old objects
  dynamic "lifecycle_rule" {
    for_each = var.lifecycle_age_days > 0 ? [1] : []
    content {
      condition {
        age = var.lifecycle_age_days
      }
      action {
        type = "Delete"
      }
    }
  }

  # Prevent accidental deletion of the bucket
  # Enable in production environments
  # lifecycle {
  #   prevent_destroy = true
  # }

  labels = var.labels
}

# -----------------------------------------------------------------------------
# Public Access Configuration
# Make all objects publicly readable (required for static hosting)
# -----------------------------------------------------------------------------

resource "google_storage_bucket_iam_member" "public_read" {
  bucket = google_storage_bucket.submissions.name
  role   = "roles/storage.objectViewer"
  member = "allUsers"
}

# -----------------------------------------------------------------------------
# Outputs
# -----------------------------------------------------------------------------

output "bucket_name" {
  description = "Name of the GCS bucket"
  value       = google_storage_bucket.submissions.name
}

output "bucket_url" {
  description = "URL for accessing the bucket (static hosting)"
  value       = "https://storage.googleapis.com/${google_storage_bucket.submissions.name}"
}

output "bucket_self_link" {
  description = "Self link of the bucket"
  value       = google_storage_bucket.submissions.self_link
}
