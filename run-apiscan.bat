@echo off
REM APISCAN Runner - Scans API projects and generates OpenAPI documentation
REM Usage: run-apiscan.bat [--skip-build] <project-path>
REM Example: run-apiscan.bat "C:\Users\Rudi Kristanto\prj\spring-petclinic-rest"
REM Example: run-apiscan.bat --skip-build "C:\Users\Rudi Kristanto\prj\shopizer"
REM Example: run-apiscan.bat --skip-build "C:\Users\Rudi Kristanto\inp\piggymetrics"

setlocal enabledelayedexpansion

REM Check for --skip-build flag
set SKIP_BUILD=false
set PROJECT_PATH=

if "%~1"=="--skip-build" (
    set SKIP_BUILD=true
    set PROJECT_PATH=%~2
) else if "%~1"=="-s" (
    set SKIP_BUILD=true
    set PROJECT_PATH=%~2
) else (
    set PROJECT_PATH=%~1
)

if "!PROJECT_PATH!"=="" (
    echo ERROR: Project path is required!
    echo.
    echo Usage: %~nx0 [--skip-build ^| -s] ^<project-path^>
    echo.
    echo Options:
    echo   --skip-build, -s   Skip Maven build and use existing JAR
    echo.
    echo Examples:
    echo   %~nx0 "C:\Users\Rudi Kristanto\prj\spring-petclinic-rest"
    echo   %~nx0 --skip-build "C:\Users\Rudi Kristanto\prj\shopizer"
    echo   %~nx0 -s "C:\Users\Rudi Kristanto\prj\shopizer"
    echo.
    pause
    exit /b 1
)

if not exist "!PROJECT_PATH!" (
    echo ERROR: Project path does not exist: !PROJECT_PATH!
    echo.
    pause
    exit /b 1
)

REM Extract project name from path for output file naming
for %%F in ("!PROJECT_PATH!") do set PROJECT_NAME=%%~nxF

REM Build JAR unless skip-build is specified
if "!SKIP_BUILD!"=="false" (
    echo Building APISCAN...
    call mvn clean package -DskipTests -q
    
    if !ERRORLEVEL! neq 0 (
        echo Build failed!
        pause
        exit /b 1
    )
) else (
    echo Skipping build, using existing JAR...
    if not exist "apiscan-cli\target\apiscan-cli-1.0.0-SNAPSHOT-jar-with-dependencies.jar" (
        echo ERROR: JAR file not found! Please build the project first.
        echo Run without --skip-build flag to build the project.
        pause
        exit /b 1
    )
)

echo Running APISCAN on %PROJECT_NAME%...
echo.
echo The OpenAPI specification will be generated as:
echo   %CD%\%PROJECT_NAME%-openapi.yaml
echo.

java -jar apiscan-cli\target\apiscan-cli-1.0.0-SNAPSHOT-jar-with-dependencies.jar "!PROJECT_PATH!"

echo.
echo =========================================================
echo Check the generated OpenAPI file at:
echo   %CD%\%PROJECT_NAME%-openapi.yaml
echo =========================================================
pause