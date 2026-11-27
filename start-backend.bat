@echo off
chcp 65001 >nul
title Start Backend

echo ========================================
echo    Start Backend (Spring Boot)
echo ========================================
echo.

cd /d "%~dp0backend"

echo Check Java...
java -version 2>&1
if %errorlevel% neq 0 (
    echo [Error] Java not found, please install JDK 17+
    pause
    exit /b 1
)

echo.
echo Check Maven...
mvn -version 2>&1
if %errorlevel% neq 0 (
    echo [Error] Maven not found
    pause
    exit /b 1
)

echo.
echo Start backend...
mvn spring-boot:run
