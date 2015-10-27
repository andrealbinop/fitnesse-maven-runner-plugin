fitnesse-maven-runner-plugin [![Build Status](https://travis-ci.org/andreptb/fitnesse-maven-runner-plugin.svg?branch=master)](https://travis-ci.org/andreptb/fitnesse-maven-runner-plugin) [![Coverage Status](https://coveralls.io/repos/andreptb/fitnesse-maven-runner-plugin/badge.svg?branch=master&service=github)](https://coveralls.io/github/andreptb/fitnesse-maven-runner-plugin?branch=master) [![Maven  Central](https://img.shields.io/maven-central/v/com.github.andreptb/fitnesse-maven-runner-plugin.svg)](https://maven-badges.herokuapp.com/maven-central/com.github.andreptb/fitnesse-maven-runner-plugin/)
==============

[Maven](https://maven.apache.org/plugins/) plugin designed to run [FitNesse](https://github.com/unclebob/fitnesse). This project is still in early development stage.

Check [here](http://andreptb.github.io/fitnesse-maven-runner-plugin/plugin-info.html) for detailed information on available goals.

## Installation

* You just need to configure the plugin as described [here](http://andreptb.github.io/fitnesse-maven-runner-plugin/plugin-info.html):
```xml
<project>
	<build>
		<plugins>
			...
			<plugin>
			  <groupId>com.github.andreptb</groupId>
			  <artifactId>fitnesse-maven-runner-plugin</artifactId>
			  <version>0.2.1</version>
			</plugin>
		</plugins>
	</build>
	...
</project>
```

## Running

```
mvn fitnesserunner:run
```

## Adding plugins

[FitNesse plugins](http://www.fitnesse.org/PlugIns) must be added as the plugin dependencies. For example:

```xml
<project>
	<build>
		<plugins>
			...
			<plugin>
			  <groupId>com.github.andreptb</groupId>
			  <artifactId>fitnesse-maven-runner-plugin</artifactId>
			  <version>0.2.1</version>
				<dependencies>
					<dependency>
						<groupId>com.github.andreptb</groupId>
						<artifactId>fitnesse-selenium-slim</artifactId>
						<version>0.9.0</version>
					</dependency>
				</dependencies>
			</plugin>
		</plugins>
	</build>
	...
</project>
```

## FitNesse classpath

Since FitNesse runs within a Maven classpath context, you can configure [FitNesse classpath](http://www.fitnesse.org/FitNesse.FullReferenceGuide.UserGuide.WritingAcceptanceTests.ClassPath) in your test page with the following:

```
!path ${FITNESSE_CLASSPATH}

...your test code...
```

### Testing and Building

* Running tests:
```
mvn test -Dgpg.skip
```


* To build this plugin and add to maven local repository:
```
mvn install -Dgpg.skip
```
