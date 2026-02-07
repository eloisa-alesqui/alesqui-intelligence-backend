# GitHub Actions Workflows

## Docker Publishing Workflow

The `docker-publish.yml` workflow automatically builds and publishes Docker images to Docker Hub.

### Setup Instructions

After this PR is merged, the repository owner needs to complete the following setup:

#### 1. Create Docker Hub Access Token

1. Go to https://hub.docker.com/settings/security
2. Click "New Access Token"
3. Configuration:
   - **Name**: `github-actions-backend`
   - **Permissions**: Read, Write, Delete
4. Copy the token (it will only be shown once)

#### 2. Add Secret to GitHub Repository

1. Go to: https://github.com/eloisa-alesqui/alesqui-intelligence-backend/settings/secrets/actions
2. Click "New repository secret"
3. Configuration:
   - **Name**: `DOCKERHUB_TOKEN`
   - **Value**: [paste the token from step 1]
4. Click "Add secret"

#### 3. Test the Workflow

1. Make a small change (e.g., update README.md)
2. Commit and push to the `main` branch
3. Go to the "Actions" tab in GitHub to see the workflow running
4. After ~5-10 minutes, check Docker Hub for the new image at: https://hub.docker.com/r/alesquiintelligence/backend

### How It Works

The workflow is automatically triggered by:

- **Push to `main` branch**: Builds and publishes image with `latest` tag
- **Push a version tag** (e.g., `v1.0.0`): Builds and publishes with semantic version tags (`1.0.0`, `1.0`, `latest`)
- **Manual trigger**: Can be run manually from the "Actions" tab using "workflow_dispatch"

### Docker Image Tags

The workflow generates the following tags:

- `latest` - Always points to the most recent build from the main branch
- `main` - The main branch identifier
- `v1.0.0` - Full semantic version (when a version tag is pushed)
- `1.0` - Major.minor version (when a version tag is pushed)
- `main-<sha>` - Branch name with commit SHA

### Benefits

✅ **Fully automated** - No manual `docker build`/`docker push` needed  
✅ **Works online** - No local Docker installation required  
✅ **Version tagging** - Automatic semantic versioning with git tags  
✅ **Build cache** - GitHub Actions cache for faster builds  
✅ **Professional DevOps** - Industry-standard CI/CD practice

### Troubleshooting

If the workflow fails:

1. Check that the `DOCKERHUB_TOKEN` secret is properly configured
2. Verify that the Docker Hub repository exists: `alesquiintelligence/backend`
3. Ensure the Docker Hub account has permissions to push to the repository
4. Review the workflow logs in the "Actions" tab for specific error messages
