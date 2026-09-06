package org.gradle.wrapper;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Small transparent bootstrap used only because the generator environment could
 * not fetch Gradle's official binary wrapper JAR. It honors the project's
 * gradle-wrapper.properties, verifies distributionSha256Sum, downloads/unpacks
 * Gradle into ~/.gradle, then delegates to the real Gradle executable.
 *
 * After the first successful build you can replace this bootstrap with Gradle's
 * official wrapper by running: ./gradlew wrapper --gradle-version 9.6.1
 */
public final class GradleWrapperMain {
    public static void main(String[] args) throws Exception {
        Path project = locateProject(Paths.get(System.getProperty("user.dir")).toAbsolutePath());
        Path propsPath = project.resolve("gradle/wrapper/gradle-wrapper.properties");
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(propsPath)) { props.load(in); }

        String url = require(props, "distributionUrl");
        String sha = require(props, "distributionSha256Sum").toLowerCase(Locale.ROOT);
        String fileName = url.substring(url.lastIndexOf('/') + 1);
        String baseName = fileName.endsWith(".zip") ? fileName.substring(0, fileName.length() - 4) : fileName;

        Path cache = Paths.get(System.getProperty("user.home"), ".gradle", "wrapper", "dists", "neonorbit", baseName);
        Path install = cache.resolve("install");
        Path marker = cache.resolve(".complete");
        boolean complete = Files.isRegularFile(marker) && Files.readString(marker).trim().equalsIgnoreCase(sha);
        Path executable = complete ? findGradleExecutable(install) : null;

        if (executable == null) {
            Files.createDirectories(cache);
            Path zip = cache.resolve(fileName);
            if (!Files.exists(zip) || !sha256(zip).equals(sha)) {
                Files.deleteIfExists(zip);
                System.out.println("Downloading " + url);
                HttpClient client = HttpClient.newBuilder()
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build();
                HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
                HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(zip));
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new IOException("Gradle download failed: HTTP " + response.statusCode());
                }
                String actual = sha256(zip);
                if (!actual.equals(sha)) {
                    Files.deleteIfExists(zip);
                    throw new SecurityException("Gradle distribution checksum mismatch. Expected " + sha + " but got " + actual);
                }
            }
            deleteRecursively(install);
            Files.createDirectories(install);
            unzip(zip, install);
            executable = findGradleExecutable(install);
            if (executable == null) throw new IOException("Gradle executable not found after extraction");
            Files.writeString(marker, sha + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        }

        if (!isWindows()) executable.toFile().setExecutable(true, false);
        List<String> command = new ArrayList<>();
        command.add(executable.toString());
        command.addAll(Arrays.asList(args));
        ProcessBuilder pb = new ProcessBuilder(command)
                .directory(project.toFile())
                .inheritIO();
        int code = pb.start().waitFor();
        System.exit(code);
    }

    private static Path locateProject(Path start) throws IOException {
        Path p = start;
        while (p != null) {
            if (Files.isRegularFile(p.resolve("gradle/wrapper/gradle-wrapper.properties"))) return p;
            p = p.getParent();
        }
        throw new IOException("Could not locate gradle/wrapper/gradle-wrapper.properties from " + start);
    }

    private static String require(Properties p, String key) {
        String value = p.getProperty(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing " + key + " in gradle-wrapper.properties");
        return value;
    }

    private static Path findGradleExecutable(Path install) throws IOException {
        if (!Files.isDirectory(install)) return null;
        String name = isWindows() ? "gradle.bat" : "gradle";
        try (var stream = Files.walk(install, 4)) {
            return stream.filter(p -> p.getFileName().toString().equals(name) && p.getParent() != null && p.getParent().getFileName().toString().equals("bin"))
                    .findFirst().orElse(null);
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static void unzip(Path zip, Path dest) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                Path out = dest.resolve(e.getName()).normalize();
                if (!out.startsWith(dest)) throw new IOException("Blocked unsafe zip path: " + e.getName());
                if (e.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    Files.copy(zis, out, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
    }

    private static String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) >= 0) digest.update(buf, 0, n);
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : digest.digest()) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var stream = Files.walk(root)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ex) { throw new UncheckedIOException(ex); }
            });
        } catch (UncheckedIOException ex) {
            throw ex.getCause();
        }
    }
}
