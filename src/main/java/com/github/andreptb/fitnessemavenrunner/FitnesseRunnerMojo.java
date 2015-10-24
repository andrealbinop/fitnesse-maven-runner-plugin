
package com.github.andreptb.fitnessemavenrunner;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.Range;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.mutable.MutableObject;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Build;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.twdata.maven.mojoexecutor.MojoExecutor;
import org.twdata.maven.mojoexecutor.MojoExecutor.Element;
import org.twdata.maven.mojoexecutor.MojoExecutor.ExecutionEnvironment;

/**
 * Plugin allow configuration to run FitNesse, resembling
 * <a href="http://www.fitnesse.org/FitNesse.FullReferenceGuide.UserGuide.AdministeringFitNesse.CommandLineArguments">regular FitNesse command line</a>
 * but providing extra features such as classpath configuration and so on.
 */
@Mojo(name = "run", requiresDependencyResolution = ResolutionScope.TEST)
public class FitnesseRunnerMojo extends AbstractMojo {

	/**
	 * If no port configuration is supplied, the first obtained port will be used, starting from the constant value below
	 */
	private static final Range<Integer> PORT_RANGE = Range.between(8000, 9000);

	/**
	 * Reference to maven project
	 */
	@Parameter(defaultValue = "${project}", readonly = true)
	private MavenProject project;

	/**
	 * Reference to the current maven session
	 */
	@Parameter(defaultValue = "${session}", readonly = true)
	private MavenSession mavenSession;

	/**
	 * The Maven BuildPluginManager component.
	 */
	@Component
	private BuildPluginManager pluginManager;

	/**
	 * FitNesse port to start in WEB mode. Defaults to 8000
	 */
	@Parameter(property = "fitnesse.port")
	private Integer port;

	/**
	 * The directory in which FitNesse expects to find its page root.
	 */
	@Parameter(property = "fitnesse.rootPath")
	private String rootPath;

	/**
	 * The directory in which FitNesse looks for top level pages.
	 */
	@Parameter(property = "fitnesse.fitNesseRoot", defaultValue = "FitNesseRoot")
	private String fitNesseRoot;

	/**
	 * Load an alternative configuration file. The file format adhere's to the java standard of property files. If this option is not provided, the default file plugins.properties will be loaded if it
	 * exists.
	 */
	@Parameter(property = "fitnesse.configFile")
	private String configFile;

	/**
	 * Creates a variable to be used by fitnesse containing the classpath available along the run command.
	 */
	@Parameter(property = "fitnesse.classpath.variable", defaultValue = "fitnesse.classpath")
	private String fitnesseClasspathVariable;

	/**
	 * Indicates if the project dependencies should be used when executing the main class.
	 */
	@Parameter(property = "fitnesse.includeProjectDependencies", defaultValue = "false")
	private boolean includeProjectDependencies;

	/**
	 * If informed, will run the command and then exit, useful to run tests and then exit. For example: <b>mvn fitnesserunner:run -Dfitnesse.command=SuiteToRun?suite&format=text</b>
	 * <br/>
	 * Check <a href="http://www.fitnesse.org/FitNesse.FullReferenceGuide.UserGuide.AdministeringFitNesse.RestfulServices">here</a> for all available options.
	 * Note that only <b>resource?responder&inputs</b> part of the URL needs be informed
	 */
	@Parameter(property = "fitnesse.command")
	private String command;

	/**
	 * Redirect command output. This is most useful in conjunction with the {@link #command} option
	 */
	@Parameter(property = "fitnesse.redirectOutput")
	private String redirectOutput;

	/**
	 * Creates a configuration of maven-exec-plugin to run FitNesse based on this plugin configuration.
	 */
	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		Plugin plugin = MojoExecutor.plugin("org.codehaus.mojo", "exec-maven-plugin", "1.4.0");
		Collection<Element> configuration = new ArrayList<>();
		MojoExecutor.element("includeProjectDependencies", Boolean.FALSE.toString());
		configuration.add(MojoExecutor.element("mainClass", "fitnesseMain.FitNesseMain"));
		configuration.add(MojoExecutor.element("commandlineArgs", buildCommandLineArgs()));
		String classpathString = addClasspathConfigurationElement(configuration);
		addSystemPropertiesConfigurationElement(configuration, classpathString);
		ExecutionEnvironment executionEnviroment = MojoExecutor.executionEnvironment(this.project, this.mavenSession, this.pluginManager);
		MojoExecutor.executeMojo(plugin, "java", MojoExecutor.configuration(configuration.toArray(new Element[configuration.size()])), executionEnviroment);
	}

	private void addSystemPropertiesConfigurationElement(Collection<Element> configuration, String classpathString) {
		Collection<Element> envs = new ArrayList<>();
		envs.add(property(this.fitnesseClasspathVariable, classpathString));
		Map<Object, Object> properties = new HashMap<>();
		properties.putAll(this.project.getProperties());
		properties.putAll(this.mavenSession.getUserProperties());
		for (Entry<Object, Object> entry : properties.entrySet()) {
			envs.add(property(entry.getKey(), entry.getValue()));
			getLog().debug("Adding system property to Fitnesse: " + entry);
		}
		configuration.add(MojoExecutor.element("systemProperties", envs.toArray(new Element[envs.size()])));
	}

	private String addClasspathConfigurationElement(Collection<Element> config) throws MojoExecutionException {
		Collection<Element> classpath = new ArrayList<>();
		Collection<URL> classpathSources = new ArrayList<>();
		Collection<String> classpathElements = new HashSet<>();
		CollectionUtils.addAll(classpathSources, ((PluginDescriptor) getPluginContext().get("pluginDescriptor")).getClassRealm().getURLs());
		for (URL classpathEntry : ((PluginDescriptor) getPluginContext().get("pluginDescriptor")).getClassRealm().getURLs()) {
			classpathElements.add(classpathEntry.getFile());
		}
		if (this.includeProjectDependencies) {
			Build build = this.project.getBuild();
			classpathElements.add(build.getOutputDirectory());
			classpathElements.add(build.getTestOutputDirectory());
			for (Artifact artifact : this.project.getArtifacts()) {
				classpathElements.add(artifact.getFile().getAbsolutePath());
			}
		}
		for (URL artifactUrl : classpathSources) {
			String file = artifactUrl.getFile();
			classpath.add(MojoExecutor.element("additionalClasspathElement", file));
			classpathElements.add(file);
		}
		config.add(MojoExecutor.element("additionalClasspathElements", classpath.toArray(new Element[classpath.size()])));
		return StringUtils.join(classpathElements, ":");
	}

	private Element property(Object key, Object value) {
		return MojoExecutor.element("property", MojoExecutor.element("key", key.toString()), MojoExecutor.element("value", value.toString()));
	}

	private String buildCommandLineArgs() throws MojoExecutionException {
		Collection<String> cmd = new ArrayList<>();
		cmd.add("-r " + this.fitNesseRoot);
		try {
			String rootDir = determineRootDir();
			if (StringUtils.isNotBlank(rootDir)) {
				cmd.add("-d " + rootDir);
			}
		} catch (IOException e) {
			throw new MojoExecutionException("Unexpected IO error determining fitnesse root dir", e);
		}
		if (StringUtils.isNotBlank(this.command)) {
			cmd.add("-c " + this.command);
		}
		if (StringUtils.isNotBlank(this.redirectOutput)) {
			cmd.add("-b " + Paths.get(this.redirectOutput).normalize().toString());
		}
		cmd.add("-p " + determinePort());
		return StringUtils.join(cmd, StringUtils.SPACE);
	}

	private String determineRootDir() throws IOException {
		if (StringUtils.isNotBlank(this.rootPath)) {
			return Paths.get(this.rootPath).normalize().toString();
		}
		final MutableObject<String> fitnesseRootDir = new MutableObject<>(StringUtils.EMPTY);
		Files.walkFileTree(this.project.getBasedir().toPath(), new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
				if (dir.endsWith(FitnesseRunnerMojo.this.fitNesseRoot)) {
					fitnesseRootDir.setValue(dir.toString());
					return FileVisitResult.TERMINATE;
				}
				return super.preVisitDirectory(dir, attrs);
			}
		});
		return fitnesseRootDir.getValue();
	}

	private int determinePort() throws MojoExecutionException {
		if (this.port != null) {
			return this.port;
		}
		return getAvailablePort(FitnesseRunnerMojo.PORT_RANGE.getMinimum());
	}

	private int getAvailablePort(Integer port) throws MojoExecutionException {
		if (!FitnesseRunnerMojo.PORT_RANGE.contains(port)) {
			throw new MojoExecutionException("Unable to find an available port within the range tried: " + FitnesseRunnerMojo.PORT_RANGE);
		}
		try (ServerSocket socket = new ServerSocket(port)) {
			return port;
		} catch (IOException e) {
			return getAvailablePort(port + NumberUtils.INTEGER_ONE);
		}
	}

}
