@echo off
title Test SAP Auto POJO Conversion
echo ===================================================
echo   Running SAP Auto POJO Deserialization Test
echo ===================================================
echo.

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

call "%MVN_CMD%" test-compile exec:java -Dexec.mainClass="projectsap.sap.bill.TestAutoPojoConversion" -Dexec.classpathScope="test"

pause
