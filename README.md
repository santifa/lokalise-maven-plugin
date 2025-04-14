# Lokalise Maven Plugin

A simple Maven plugin that downloads `.properties` translation files from [Lokalise](https://lokalise.com) during your build or release process.

- Downloads multiple languages in a single request
- Supports filename prefixing (e.g. `messages_en.properties`)
- Automatically resolves all project languages when `languageCodes = "*"`
- Uses the Lokalise API v2

---

## Usage

### 📦 Maven Central*g*

Available via Maven Central:

```xml
<dependency>
  <groupId>xyz.rtzptz</groupId>
  <artifactId>lokalise-maven-plugin</artifactId>
  <version>1.0.0</version>
</dependency>
```

[View on Maven Central](https://search.maven.org/search?q=g:xyz.rtzptz%20a:lokalise-maven-plugin)

## 🛠 Plugin Configuration (in your project’s `pom.xml`):

```xml
<build>
  <plugins>
    <plugin>
      <groupId>xyz.rtzptz</groupId>
      <artifactId>lokalise-maven-plugin</artifactId>
      <version>0.1.1</version> <!-- Replace with latest -->
      <configuration>
        <apiToken>${env.LOKALISE_API_TOKEN}</apiToken>
        <projectId>your_project_id</projectId>
        <languageCodes>*</languageCodes> <!-- or "de,en,fr" -->
        <filePrefix>messages_</filePrefix> <!-- optional -->
        <outputDir>${project.basedir}/src/main/resources/i18n</outputDir>
        <placeholderFormat>icu</placeholderFormat>
      </configuration>
    </plugin>
  </plugins>
</build>
```

### Execute manually

`mvn xyz.rtzptz:lokalise-maven-plugin:1.0.0:download-translations`

### Configuration Parameters

| Parameter           | Required | Description                                                                                       |
|---------------------|----------|:--------------------------------------------------------------------------------------------------|
| `apiToken`          | ✅        | Lokalise API token (X-Api-Token)                                                                  |
| `projectId`         | ✅        | Your Lokalise project ID                                                                          |
| `languageCodes`     | ❌        | Comma-separated list (e.g. de,en) or * for all                                                    |
| `filePrefix`        | ❌        | Prefix for filenames (e.g. messages_)                                                             |
| `outputDir`         | ❌        | Directory for downloaded .properties files                                                        |
| `placeholderFormat` | ❌        | Format for [placeholders](https://developers.lokalise.com/reference/api-plurals-and-placeholders) |

## 📄 License

Apache License, Version 2.0 © Henrik Jürges