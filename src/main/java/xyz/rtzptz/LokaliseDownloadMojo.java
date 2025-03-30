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

import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.util.List;
import java.util.zip.ZipInputStream;

/**
 * Goal which downloads the latest translations from a lokalise project.
 */
@Mojo(name = "download-translations", defaultPhase = LifecyclePhase.INSTALL)
public class LokaliseDownloadMojo
    extends AbstractMojo
{
    /**
     * Get access to the parent project.
     */
    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    MavenProject project;

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
     * Set the language codes which should be downloaded from lokalise.
     * For each language code an individual java properties file is downloaded.
     */
    @Parameter(property = "languageCodes", required = true)
    private String languageCodes;

    /**
     * Set the directory where the downloaded java property files should be stored.
     */
    @Parameter(property = "outputDir", defaultValue = "${project.basedir}/src/main/resources/i18n")
    private File outputDir;

 public void execute() throws MojoExecutionException {
     try {
         String[] langs = languageCodes.split(",");
         for (String lang : langs) {
             getLog().info("Fetching translations for language: " + lang);

             URL url = new URL("https://api.lokalise.com/api2/projects/" + projectId + "/files/download");
             String body = String.format(
                     "{\"format\":\"properties\",\"original_filenames\":false,\"filter_langs\":[\"%s\"]}",
                     lang.trim()
             );

             HttpURLConnection conn = (HttpURLConnection) url.openConnection();
             conn.setRequestMethod("POST");
             conn.setRequestProperty("X-Api-Token", apiToken);
             conn.setRequestProperty("Content-Type", "application/json");
             conn.setDoOutput(true);
             conn.getOutputStream().write(body.getBytes());

             BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
             String line, bundleUrl = null;
             while ((line = in.readLine()) != null) {
                 if (line.contains("bundle_url")) {
                     bundleUrl = line.replaceAll(".*\"bundle_url\"\\s*:\\s*\"(.*?)\".*", "$1").replace("\\/", "/");
                     break;
                 }
             }
             in.close();

             if (bundleUrl == null) {
                 throw new MojoExecutionException("bundle_url not found in Lokalise response");
             }

             // Download ZIP and extract
             HttpURLConnection zipConn = (HttpURLConnection) new URL(bundleUrl).openConnection();
             try (ZipInputStream zipIn = new ZipInputStream(zipConn.getInputStream())) {
                 Files.createDirectories(outputDir.toPath());

                 java.util.zip.ZipEntry entry;
                 while ((entry = zipIn.getNextEntry()) != null) {
                     if (!entry.isDirectory()) {
                         File outFile = new File(outputDir, lang + ".properties");
                         try (FileOutputStream out = new FileOutputStream(outFile)) {
                             zipIn.transferTo(out);
                         }
                         getLog().info("Saved: " + outFile.getAbsolutePath());
                     }
                     zipIn.closeEntry();
                 }
             }
         }
     } catch (Exception e) {
         throw new MojoExecutionException("Failed to fetch Lokalise translations", e);
     }
 }
}
