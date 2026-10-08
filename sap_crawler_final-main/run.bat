@echo off
title SAP Subscription Billing Dashboard (PostgreSQL / Maven)
echo ===================================================
echo   SAP Subscription Billing Dashboard (Maven)
echo ===================================================
echo.

:: 1. PostgreSQL Credentials (Change if needed)
if "%DB_USER%"=="" set "DB_USER=postgres"
if "%DB_PASS%"=="" set "DB_PASS=flash"
if "%DB_NAME%"=="" set "DB_NAME=sap_bills"

:: 2. Check Java
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Java is not found in system PATH.
    echo Please install JDK 21+ and set JAVA_HOME.
    pause
    exit /b 1
)

:: 3. Resolve Maven Command
set "MVN_CMD=mvn"
where mvn >nul 2>&1
if %errorlevel% neq 0 (
    if exist "C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2023.3.2\plugins\maven\lib\maven3\bin\mvn.cmd" (
        set "MVN_CMD=C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2023.3.2\plugins\maven\lib\maven3\bin\mvn.cmd"
    ) else (
        echo [ERROR] Maven is not installed or not in PATH.
        echo Please install Apache Maven or run using IntelliJ Maven.
        pause
        exit /b 1
    )
)

echo [1/2] Compiling Java sources with Maven...
call "%MVN_CMD%" compile
if %errorlevel% neq 0 (
    echo [ERROR] Maven compilation failed!
    pause
    exit /b 1
)

echo.
echo [2/2] Starting SAP Bill Dashboard Server...
echo ===================================================
echo   Connecting to PostgreSQL (db: %DB_NAME%, user: %DB_USER%)
echo   Opening browser at http://localhost:8080 ...
echo   Press Ctrl+C to stop server.
echo ===================================================
echo.

:: Open browser after 3 seconds
start /b cmd /c "timeout /t 3 /nobreak >nul && start http://localhost:8080"

:: Launch Maven Server with PostgreSQL parameters
call "%MVN_CMD%" exec:java "-Ddb.user=%DB_USER%" "-Ddb.pass=%DB_PASS%" "-Ddb.name=%DB_NAME%"

pause
