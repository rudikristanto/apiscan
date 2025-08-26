@echo off
REM APISCAN Runner - Scans API projects and generates OpenAPI documentation
REM Usage: run-apiscan.bat [--skip-build] [--microservices-output combined|separate] <project-path>
REM Example: run-apiscan.bat "C:\Users\Rudi Kristanto\prj\spring-petclinic-rest"
REM Example: run-apiscan.bat --skip-build "C:\Users\Rudi Kristanto\prj\shopizer"
REM Example: run-apiscan.bat --microservices-output separate "C:\Users\Rudi Kristanto\inp\C:\Users\Rudi Kristanto\inp\Spring-Boot-Microservices-Banking-Application"

setlocal enabledelayedexpansion

REM Parse command line arguments using simpler approach
set SKIP_BUILD=false
set MICROSERVICES_OUTPUT=
set PROJECT_PATH=

REM Handle different argument patterns
if "%~1"=="--skip-build" (
    set SKIP_BUILD=true
    if "%~2"=="--microservices-output" (
        set MICROSERVICES_OUTPUT=%~3
        set PROJECT_PATH=%~4
    ) else (
        set PROJECT_PATH=%~2
    )
) else if "%~1"=="-s" (
    set SKIP_BUILD=true
    if "%~2"=="--microservices-output" (
        set MICROSERVICES_OUTPUT=%~3
        set PROJECT_PATH=%~4
    ) else (
        set PROJECT_PATH=%~2
    )
) else if "%~1"=="--microservices-output" (
    set MICROSERVICES_OUTPUT=%~2
    set PROJECT_PATH=%~3
) else (
    set PROJECT_PATH=%~1
)

if "!PROJECT_PATH!"=="" (
    goto :show_usage
)

REM Validate microservices output mode if specified
if not "!MICROSERVICES_OUTPUT!"=="" (
    if not "!MICROSERVICES_OUTPUT!"=="combined" (
        if not "!MICROSERVICES_OUTPUT!"=="separate" (
            echo ERROR: Invalid microservices output mode: !MICROSERVICES_OUTPUT!
            echo Valid options are: combined, separate
            echo.
            goto :show_usage
        )
    )
)

goto :validate_path

:show_usage
echo ERROR: Invalid arguments or project path is required!
echo.
echo Usage: %~nx0 [--skip-build ^| -s] [--microservices-output combined^|separate] ^<project-path^>
echo.
echo Options:
echo   --skip-build, -s                    Skip Maven build and use existing JAR
echo   --microservices-output combined     Generate single OpenAPI file for all microservices ^(default^)
echo   --microservices-output separate     Generate individual OpenAPI files per microservice
echo.
echo Examples:
echo   %~nx0 "C:\Users\Rudi Kristanto\prj\spring-petclinic-rest"
echo   %~nx0 --skip-build "C:\Users\Rudi Kristanto\prj\shopizer"
echo   %~nx0 --microservices-output separate "C:\Users\Rudi Kristanto\inp\banking-microservices"
echo   %~nx0 --skip-build --microservices-output combined "C:\Users\Rudi Kristanto\inp\banking-microservices"
echo.
pause
exit /b 1

:validate_path

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

REM Prepare the command with optional microservices output parameter
set JAVA_CMD=java -jar apiscan-cli\target\apiscan-cli-1.0.0-SNAPSHOT-jar-with-dependencies.jar
if not "!MICROSERVICES_OUTPUT!"=="" (
    set JAVA_CMD=!JAVA_CMD! --microservices-output !MICROSERVICES_OUTPUT!
)
set JAVA_CMD=!JAVA_CMD! "!PROJECT_PATH!"

REM Show expected output based on microservices mode
echo.
if "!MICROSERVICES_OUTPUT!"=="separate" (
    echo Individual OpenAPI files will be generated for each microservice in:
    echo   %CD%\^<service-name^>-openapi.yaml
) else (
    echo The OpenAPI specification will be generated as:
    echo   %CD%\%PROJECT_NAME%-openapi.yaml
)
echo.

!JAVA_CMD!

echo.
echo =========================================================
if "!MICROSERVICES_OUTPUT!"=="separate" (
    echo Check the generated OpenAPI files in the current directory:
    echo   %CD%\*-openapi.yaml
) else (
    echo Check the generated OpenAPI file at:
    echo   %CD%\%PROJECT_NAME%-openapi.yaml
)
echo =========================================================
pause