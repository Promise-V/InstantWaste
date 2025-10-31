Write-Host "🚀 Deploying to Cloud Run..." -ForegroundColor Green

gcloud run deploy instant-waste `
  --source . `
  --region us-central1 `
  --platform managed `
  --allow-unauthenticated

Write-Host "✅ Deployment complete!" -ForegroundColor Green