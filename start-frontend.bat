@echo off
chcp 65001 >nul
title Start Frontend

echo ========================================
echo    Start Frontend (Vue.js)
echo ========================================
echo.

cd /d "%~dp0frontend"

echo Check Node.js...
node -v 2>&1
if %errorlevel% neq 0 (
    echo [Error] Node.js not found, please install Node.js 18+
    pause
    exit /b 1
)

echo.
echo Check npm...
npm -v 2>&1
if %errorlevel% neq 0 (
    echo [Error] npm not found
    pause
    exit /b 1
)

echo.
echo Check dependencies...
if not exist "node_modules" (
    echo First run, install dependencies...
    npm install
)

echo.
echo Start frontend...
npm run dev
