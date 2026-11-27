@echo off
chcp 65001 >nul
title SunMao Dev Launcher

echo ========================================
echo    SunMao Dev Environment Startup
echo ========================================
echo.

if not defined DB_PORT set DB_PORT=3306
echo [1/3] Check MySQL on port %DB_PORT%...
netstat -an | findstr ":%DB_PORT%" >nul
if %errorlevel% neq 0 (
    echo [Warning] MySQL not running on port %DB_PORT%
    echo [Tip] Please start MySQL service first ^(or: docker compose up -d^)
    echo.
    pause
    exit /b 1
)
echo [OK] MySQL service running
echo.

echo [2/3] Start backend (Spring Boot)...
start "SUNMAO-Backend" /D "%~dp0backend" cmd /k "mvn spring-boot:run"
echo [OK] Backend start command sent
echo.

echo [3/3] Wait for backend (15s)...
timeout /t 15 /nobreak >nul

echo [OK] Start frontend (Vue.js)...
start "SUNMAO-Frontend" /D "%~dp0frontend" cmd /k "npm run dev"
echo [OK] Frontend start command sent
echo.

echo ========================================
echo    Startup Complete!
echo ========================================
echo.
echo Access URLs:
echo   Frontend: http://localhost:5220
echo   Backend:  http://localhost:5221
echo   API Doc:  http://localhost:5221/swagger-ui.html
echo.
echo Press any key to close...
pause >nul
