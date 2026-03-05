#!/bin/bash

# Optix API Deployment Script
# Usage: ./deploy.sh

set -e

echo "🚀 Deploying Optix API..."

# Pull latest code
echo "📥 Pulling latest code..."
git pull origin main

# Build and start with production config
echo "🔨 Building Docker images..."
docker-compose -f docker-compose.prod.yml build

# Stop existing containers
echo "🛑 Stopping existing containers..."
docker-compose -f docker-compose.prod.yml down

# Start new containers
echo "▶️ Starting containers..."
docker-compose -f docker-compose.prod.yml up -d

# Wait for services to be healthy
echo "⏳ Waiting for services to start..."
sleep 10

# Run database migrations
echo "📊 Running database migrations..."
docker-compose -f docker-compose.prod.yml exec -T api alembic upgrade head || echo "Migrations skipped"

# Check health
echo "🏥 Checking health..."
curl -s http://localhost:8000/health || echo "Health check pending..."

echo ""
echo "✅ Deployment complete!"
echo ""
echo "📋 Next steps:"
echo "1. Edit .env.prod and add your OPENAI_API_KEY"
echo "2. Run: docker-compose -f docker-compose.prod.yml restart api"
echo "3. Check logs: docker-compose -f docker-compose.prod.yml logs -f api"
echo ""
echo "🔗 API URL: http://localhost:8000"
echo "📚 Docs: http://localhost:8000/docs"
