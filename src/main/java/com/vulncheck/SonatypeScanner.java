package com.vulncheck;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public class SonatypeScanner {


    private final SonatypeCredentials sonatypeCredentials;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SonatypeScanner(SonatypeCredentials sonatypeCredentials) {
        this.sonatypeCredentials = sonatypeCredentials;
    }


    public SonatypeScanResult scan(Path pathToProject) {

        Objects.requireNonNull(pathToProject, "pathToProject");

        if (!Files.isDirectory(pathToProject)) {
            throw new IllegalArgumentException(
                    "Project path is not a directory: " + pathToProject
            );
        }

        Path pomFile = pathToProject.resolve("pom.xml");

        if (!Files.isRegularFile(pomFile)) {
            throw new IllegalArgumentException(
                    "pom.xml not found: " + pomFile
            );
        }

        Path resultFile = pathToProject
                .resolve("target")
                .resolve("clm-results.json");

        ProcessBuilder processBuilder = getProcessBuilder("-Dclm.resultFile=" + resultFile.toAbsolutePath(), pathToProject);


        final String output;
        final int exitCode;

        try {
            Process process = processBuilder.start();

            output = new String(
                    process.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8
            );

            exitCode = process.waitFor();

        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Cannot start Sonatype scan",
                    exception
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                    "Sonatype scan was interrupted",
                    exception
            );
        }

        /*
         * Навіть при ненульовому exit code результат може існувати:
         * Sonatype може завершити Maven build помилкою через policy action Fail.
         */
        if (!Files.isRegularFile(resultFile)) {
            throw new IllegalStateException(
                    "Sonatype result file was not created."
                            + System.lineSeparator()
                            + "Exit code: "
                            + exitCode
                            + System.lineSeparator()
                            + "Expected file: "
                            + resultFile
                            + System.lineSeparator()
                            + "Maven output:"
                            + System.lineSeparator()
                            + output
            );
        }

        try {
            SonatypeScanResult result = objectMapper.readValue(
                    resultFile.toFile(),
                    SonatypeScanResult.class
            );

            return new SonatypeScanResult(
                    result.applicationId(),
                    result.scanId(),
                    result.reportHtmlUrl(),
                    result.reportPdfUrl(),
                    result.reportDataUrl(),
                    exitCode,
                    output
            );

        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Cannot parse Sonatype result file: " + resultFile,
                    exception
            );
        }
    }

    private ProcessBuilder getProcessBuilder(String resultFile, Path pathToProject) {
        List<String> command = List.of(
                "mvn",
                "-B",
                "package",
                "com.sonatype.clm:clm-maven-plugin:3.0.10-01:evaluate",
                "-DskipTests",
                "-Dclm.serverUrl=" + sonatypeCredentials.serverUrl(),
                "-Dclm.username=" + sonatypeCredentials.sonatypeUsername(),
                "-Dclm.password=" + sonatypeCredentials.sonatypePassword(),
                "-Dclm.applicationId=" + sonatypeCredentials.applicationId(),
                "-Dclm.stage=build",
                resultFile
        );

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(pathToProject.toFile());
        processBuilder.redirectErrorStream(true);
        return processBuilder;
    }


    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SonatypeScanResult(
            String applicationId,
            String scanId,
            String reportHtmlUrl,
            String reportPdfUrl,
            String reportDataUrl,
            int processExitCode,
            String processOutput) {
    }


}
