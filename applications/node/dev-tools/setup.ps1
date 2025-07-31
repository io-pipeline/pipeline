# Pipeline Dev Tools Setup Script for Windows

Write-Host "Pipeline Developer Tools - Setup" -ForegroundColor Cyan
Write-Host "================================" -ForegroundColor Cyan

# Check Node.js
try {
    $nodeVersion = node -v
    $majorVersion = [int]($nodeVersion -replace 'v(\d+).*', '$1')
    
    if ($majorVersion -lt 22) {
        Write-Host "❌ Node.js version is too old. Please upgrade to v22 or higher." -ForegroundColor Red
        Write-Host "   Current version: $nodeVersion" -ForegroundColor Red
        Write-Host "   Node.js 22 is required for native TypeScript support and modern features." -ForegroundColor Red
        exit 1
    }
    
    Write-Host "✅ Node.js $nodeVersion detected" -ForegroundColor Green
}
catch {
    Write-Host "❌ Node.js is not installed. Please install Node.js v22 or higher." -ForegroundColor Red
    Write-Host "   Visit: https://nodejs.org/" -ForegroundColor Red
    exit 1
}

# Check/Install pnpm
try {
    $pnpmVersion = pnpm -v
    Write-Host "✅ pnpm $pnpmVersion detected" -ForegroundColor Green
}
catch {
    Write-Host "📦 Installing pnpm..." -ForegroundColor Yellow
    npm install -g pnpm
    
    # Verify installation
    try {
        $pnpmVersion = pnpm -v
        Write-Host "✅ pnpm $pnpmVersion installed" -ForegroundColor Green
    }
    catch {
        Write-Host "❌ Failed to install pnpm. Please install manually:" -ForegroundColor Red
        Write-Host "   npm install -g pnpm" -ForegroundColor Red
        exit 1
    }
}

# Install dependencies
Write-Host ""
Write-Host "📦 Installing dependencies..." -ForegroundColor Yellow
pnpm install

if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Failed to install dependencies" -ForegroundColor Red
    exit 1
}

# Generate protobuf types
Write-Host ""
Write-Host "🔧 Generating protobuf types..." -ForegroundColor Yellow

# Backend protos
Write-Host "  - Generating backend types..." -ForegroundColor Gray
Set-Location backend
& .\node_modules\.bin\buf generate
if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Failed to generate backend protobuf types" -ForegroundColor Red
    exit 1
}

# Frontend protos
Write-Host "  - Generating frontend types..." -ForegroundColor Gray
Set-Location ..\frontend
& .\node_modules\.bin\buf generate ..\backend\proto
if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Failed to generate frontend protobuf types" -ForegroundColor Red
    exit 1
}

Set-Location ..

Write-Host ""
Write-Host "✅ Setup complete!" -ForegroundColor Green
Write-Host ""
Write-Host "To start the development servers:" -ForegroundColor Cyan
Write-Host "  1. Backend:  cd backend; pnpm dev" -ForegroundColor White
Write-Host "  2. Frontend: cd frontend; pnpm dev (in a new terminal)" -ForegroundColor White
Write-Host "  3. Open http://localhost:5173 in your browser" -ForegroundColor White
Write-Host ""
Write-Host "Happy coding! 🚀" -ForegroundColor Green