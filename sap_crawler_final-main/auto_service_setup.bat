@echo off
setlocal enabledelayedexpansion

title SAP Auto Service Setup and POJO Generator
echo ==================================================================
echo   SAP 1-Click Auto Service Setup and POJO Generator
echo ==================================================================
echo.

set "SERVICE_NAME=%~1"
set "SERVICE_URL=%~2"

if "%SERVICE_NAME%"=="" set "SERVICE_NAME=subscription"
if "%SERVICE_URL%"=="" set "SERVICE_URL=https://sandbox.api.sap.com/subscriptionbilling/api/subscription/v1/subscriptions"

:: Resolve Maven Command
set "MVN_CMD=mvn"
where mvn >nul 2>&1
if %errorlevel% neq 0 (
    if exist "C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2023.3.2\plugins\maven\lib\maven3\bin\mvn.cmd" (
        set "MVN_CMD=C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2023.3.2\plugins\maven\lib\maven3\bin\mvn.cmd"
    ) else (
        echo [ERROR] Maven not found.
        pause
        exit /b 1
    )
)

echo.
echo Service: %SERVICE_NAME%
echo Endpoint: %SERVICE_URL%
echo.
echo [1/2] Compiling Setup Manager...
call "%MVN_CMD%" compile
if %errorlevel% neq 0 (
    echo [ERROR] Maven compilation failed!
    pause
    exit /b 1
)

:: Prompt if arguments not provided
if "%SERVICE_NAME%"=="" (
    set /p "SERVICE_NAME=Enter Service Name (e.g. usagerecords): "
)
if "%SERVICE_URL%"=="" (
    set /p "SERVICE_URL=Enter SAP Endpoint URL: "
)

:: DB Configuration
if "%DB_USER%"=="" set "DB_USER=postgres"
if "%DB_PASS%"=="" set "DB_PASS=flash"
if "%DB_NAME%"=="" set "DB_NAME=sap_bills"

echo.
echo [2/2] Running Auto Service Manager for "%SERVICE_NAME%"...
:: Pass via environment variables to prevent Windows CMD from splitting URLs on '&' and '%'
call "%MVN_CMD%" exec:java -Dexec.mainClass="projectsap.sap.common.SapAutoServiceManager" "-Ddb.user=%DB_USER%" "-Ddb.pass=%DB_PASS%" "-Ddb.name=%DB_NAME%"

echo.
echo ==================================================================
echo   Done! Check src/main/java/projectsap/sap/%SERVICE_NAME%/model
echo ==================================================================
pause
