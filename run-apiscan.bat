@echo off
REM APISCAN Runner - Scans Spring PetClinic REST API
REM This script builds the project and runs the API scanner

echo Building APISCAN...
call mvn clean package -DskipTests -q

if %ERRORLEVEL% neq 0 (
    echo Build failed!
    pause
    exit /b 1
)

echo Running APISCAN on Spring PetClinic REST...
java -jar apiscan-cli\target\apiscan-cli-1.0.0-SNAPSHOT-jar-with-dependencies.jar "C:\Users\Rudi Kristanto\prj\spring-petclinic-rest"

pause