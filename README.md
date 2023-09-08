# Kover Maven Plugin

This plugin is based on the [Gradle Plugin](https://github.com/Kotlin/kotlinx-kover).

## Usage

```xml
<build>
    <extensions>
        <extension>
            <groupId>io.github.martinvisser</groupId>
            <artifactId>kover-maven-plugin</artifactId>
            <version>{version}</version>
        </extension>
    </extensions>

    <pluginManagement>
        <plugins>
            <plugin>
                <groupId>io.github.martinvisser</groupId>
                <artifactId>kover-maven-plugin</artifactId>
                <version>{version}</version>
            </plugin>
        </plugins>

        <plugin>
            <groupId>io.github.martinvisser</groupId>
            <artifactId>kover-maven-plugin</artifactId>
            <configuration>
                <!-- Rules are optional, but if none are configured the plugin cannot verify the coverage -->
                <rules>
                    <rule>
                        <minValue>85</minValue>
                        <metric>LINE</metric>
                        <aggregation>COVERED_PERCENTAGE</aggregation>
                    </rule>
                    <rule>
                        <minValue>85</minValue>
                        <metric>BRANCH</metric>
                        <aggregation>COVERED_PERCENTAGE</aggregation>
                    </rule>
                </rules>
            </configuration>
        </plugin>
    </pluginManagement>
</build>
```

### Known issues (for contributors of this plugin)

- During executing tests the following message might
  appear: `The MojoDescriptor for the goal prepare-agent cannot be null`
    - Solved by running `mvn compile` first as it needs a `plugin.xml` in `target/classes/META-INF/maven/`.
