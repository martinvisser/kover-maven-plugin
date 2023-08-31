# Kover Maven Plugin

This plugin is based on the [Gradle Plugin](https://github.com/Kotlin/kotlinx-kover).

## Usage

```xml

<plugins>
    <plugin>
        <groupId>io.github.martinvisser</groupId>
        <artifactId>kover-maven-plugin</artifactId>
        <version>{version}</version>
        <executions>
            <execution>
                <goals>
                    <goal>prepare-agent</goal>
                    <goal>report</goal>
                    <goal>verify</goal>
                </goals>
            </execution>
        </executions>
        <configuration>
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
</plugins>
```

### Known issues
- During executing tests the following message might appear: `The MojoDescriptor for the goal prepare-agent cannot be null`
  - Solved by running `mvn compile` first as it needs a `plugin.xml` in `target/classes/META-INF/maven/`.
