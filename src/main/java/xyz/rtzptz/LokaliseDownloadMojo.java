package xyz.rtzptz;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.zip.ZipInputStream;

/**
 * Goal which downloads the latest translations from a lokalise project.
 */
@Mojo(name = "download-translations", defaultPhase = LifecyclePhase.INSTALL)
public class LokaliseDownloadMojo
        extends AbstractMojo {

    /**
     * The lokalise API key,
     * see the <a href="https://developers.lokalise.com/reference/api-authentication">lokalise documentation</a>
     */
    @Parameter(property = "apiToken", required = true)
    private String apiToken;

    /**
     * The lokalise identifier for a project,
     * see the <a href="https://developers.lokalise.com/reference/project-object">lokalise documentation</a>
     */
    @Parameter(property = "projectId", required = true)
    private String projectId;

    /**
     * Set the language codes which should be downloaded from lokalise or '*' for all translations.
     * For each language code an individual java properties file is downloaded.
     */
    @Parameter(property = "languageCodes", required = false)
    String languageCodes;

    @Parameter(property = "filePrefix", required = false, defaultValue = "")
    String filePrefix;

    /**
     * Set the directory where the downloaded java property files should be stored.
     */
    @Parameter(property = "outputDir", defaultValue = "${project.basedir}/src/main/resources/i18n")
    File outputDir;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public void execute() throws MojoExecutionException {
        try {
            List<String> langs = resolveLanguages();

            getLog().info("Requesting Lokalise bundle for languages: " + langs);
            String bundleUrl = requestDownloadUrl(langs);
            if (bundleUrl == null) throw new MojoExecutionException("bundle_url not found");

            HttpURLConnection zipConn = (HttpURLConnection) new URL(bundleUrl).openConnection();
            try (ZipInputStream zipIn = new ZipInputStream(zipConn.getInputStream())) {
                Files.createDirectories(outputDir.toPath());

                java.util.zip.ZipEntry entry;
                while ((entry = zipIn.getNextEntry()) != null) {
                    if (!entry.isDirectory()) {
                        String originalName = Paths.get(entry.getName()).getFileName().toString(); // e.g., de.properties
                        String langCode = originalName.replace(".properties", "");
                        String fileName = filePrefix + langCode + ".properties";
                        File outFile = new File(outputDir, fileName);

                        try (FileOutputStream out = new FileOutputStream(outFile)) {
                            zipIn.transferTo(out);
                        }
                        getLog().info("Saved: " + outFile.getAbsolutePath());
                    }
                    zipIn.closeEntry();
                }
            }

        } catch (Exception e) {
            throw new MojoExecutionException("Lokalise download failed", e);
        }
    }

    private List<String> resolveLanguages() throws IOException, MojoExecutionException {
        if (languageCodes == null || languageCodes.trim().isEmpty() || languageCodes.trim().equals("*")) {
            getLog().info("Fetching all language codes from Lokalise...");
            URL url = new URL("https://api.lokalise.com/api2/projects/" + projectId + "/languages");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("X-Api-Token", apiToken);
            conn.setRequestProperty("Content-Type", "application/json");

            try (InputStream in = conn.getInputStream()) {
                JsonNode root = objectMapper.readTree(in);
                List<String> allLangs = new ArrayList<>();
                for (JsonNode langNode : root.get("languages")) {
                    allLangs.add(langNode.get("lang_iso").asText());
                }
                return allLangs;
            }
        } else {
            return Arrays.asList(languageCodes.split(","));
        }
    }

    private String requestDownloadUrl(List<String> langs) throws IOException {
        URL url = new URL("https://api.lokalise.com/api2/projects/" + projectId + "/files/download");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("X-Api-Token", apiToken);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        conn.setDoOutput(true);

        Map<String, Object> body = new HashMap<>();
        body.put("format", "properties");
        body.put("original_filenames", false);
        body.put("all_platforms", true);
        body.put("bundle_structure", "%LANG_ISO%");
        body.put("plural_format", "json_string");
        body.put("placeholder_format", "printf");



        body.put("filter_langs", langs);

        String jsonBody = objectMapper.writeValueAsString(body);
        conn.getOutputStream().write(jsonBody.getBytes());

        if (conn.getResponseCode() >= 400) {
            try (InputStream err = conn.getErrorStream()) {
                String msg = new String(err.readAllBytes());
                getLog().error("Lokalise API error: " + msg);
            }
            throw new IOException("Lokalise API returned HTTP " + conn.getResponseCode());
        }
        else {
            try (InputStream in = conn.getInputStream()) {
                JsonNode root = objectMapper.readTree(in);
                return root.has("bundle_url") ? root.get("bundle_url").asText() : null;
            }
        }
    }
}
