@echo off
title SAP Database Data Viewer
echo ==================================================================
echo   SAP Database Data Viewer (PostgreSQL / H2)
echo ==================================================================
echo.

:: DB Configuration
if "%DB_USER%"=="" set "DB_USER=postgres"
if "%DB_PASS%"=="" set "DB_PASS=flash"
if "%DB_NAME%"=="" set "DB_NAME=sap_bills"

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

call "%MVN_CMD%" compile exec:java -Dexec.mainClass="projectsap.sap.bill.db.DatabaseViewer" "-Ddb.user=%DB_USER%" "-Ddb.pass=%DB_PASS%" "-Ddb.name=%DB_NAME%"

echo.
pause
