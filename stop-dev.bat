@echo off
chcp 65001 >nul
title Stop Dev Services

echo ========================================
echo    Stop Dev Services
echo ========================================
echo.

echo [1/2] Stop backend...
taskkill /FI "WINDOWTITLE eq SUNMAO-Backend*" /F >nul 2>&1
taskkill /FI "IMAGENAME eq java.exe" /FI "WINDOWTITLE eq SUNMAO-Backend*" /F >nul 2>&1
echo [OK] Backend stopped

echo [2/2] Stop frontend...
taskkill /FI "WINDOWTITLE eq SUNMAO-Frontend*" /F >nul 2>&1
taskkill /FI "IMAGENAME eq node.exe" /FI "WINDOWTITLE eq SUNMAO-Frontend*" /F >nul 2>&1
echo [OK] Frontend stopped

echo.
echo ========================================
echo    All Services Stopped
echo ========================================
echo.
pause
