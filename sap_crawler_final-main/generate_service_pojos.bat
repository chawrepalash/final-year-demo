@echo off
setlocal enabledelayedexpansion

echo ===================================================
echo   SAP Auto POJO Generator for Any SAP Service
echo ===================================================
echo.

if "%~1"=="" (
    echo [USAGE] generate_service_pojos.bat ^<path_to_spec.json^> ^<service_name^>
    echo.
    echo Example:
    echo   generate_service_pojos.bat specs/sap-bills-spec.json bills
    echo.
    pause
    exit /b 1
)

set "SPEC_FILE=%~1"
set "SERVICE_NAME=%~2"
if "%SERVICE_NAME%"=="" set "SERVICE_NAME=customservice"

:: Resolve Maven
set "MVN_CMD=mvn"
where mvn >nul 2>&1
if %errorlevel% neq 0 (
    if exist "C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2023.3.2\plugins\maven\lib\maven3\bin\mvn.cmd" (
        set "MVN_CMD=C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2023.3.2\plugins\maven\lib\maven3\bin\mvn.cmd"
    ) else (
        echo [ERROR] Maven not found. Please install Maven or run via IntelliJ.
        exit /b 1
    )
)

echo Generating Java POJOs from: %SPEC_FILE%
echo Target package: projectsap.sap.%SERVICE_NAME%.model
echo.

call "%MVN_CMD%" openapi-generator:generate ^
    -Dopenapi.generator.maven.plugin.inputSpec="%SPEC_FILE%" ^
    -Dopenapi.generator.maven.plugin.modelPackage=projectsap.sap.%SERVICE_NAME%.model ^
    -Dopenapi.generator.maven.plugin.outputDir=target/generated-sources/openapi

if %errorlevel% equ 0 (
    echo.
    echo [SUCCESS] POJOs generated successfully in target/generated-sources/openapi!
) else (
    echo.
    echo [ERROR] Generation failed.
)

pause
