
package com.github.andreptb.fitnessemavenrunner;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.Range;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.OverConstrainedVersionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import fitnesse.ConfigurationParameter;
import fitnesse.ContextConfigurator;

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
	@Parameter(defaultValue = "${plugin}", readonly = true)
	private PluginDescriptor plugin;

	@Parameter(defaultValue = "${plugin.artifacts}", readonly = true)
	private Collection<Artifact> pluginClasspath;

	/**
	 * Reference to the current maven session
	 */
	@Parameter(defaultValue = "${session}", readonly = true)
	private MavenSession mavenSession;

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

	@Parameter(property = "fitnesse.contextRoot")
	private String contextRoot;

	@Parameter(defaultValue = "com.github.andreptb.fitnessemavenrunner.FitNesseLauncher")
	private String fitNesseLauncherClass;

	/**
	 * Creates a variable to be used by fitnesse containing the classpath available along the run command.
	 */
	@Parameter(property = "fitnesse.classpath.variable", defaultValue = "FITNESSE_CLASSPATH")
	private String fitnesseClasspathVariable;

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
	 * Indicates if the project dependencies should be used when executing the main class.
	 */
	@Parameter(property = "fitnesse.includeProjectDependencies", defaultValue = "false")
	private boolean includeProjectDependencies;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		Collection<String> classpath = determineClasspath();
		execute(configuration(classpath), classpath);
	}

	private ContextConfigurator configuration(Collection<String> classpath) {
		ContextConfigurator config = ContextConfigurator.systemDefaults();
		withParameter(config, ConfigurationParameter.ROOT_PATH, determineRootPath());
		withParameter(config, ConfigurationParameter.ROOT_DIRECTORY, this.fitNesseRoot);
		withParameter(config, ConfigurationParameter.CONTEXT_ROOT, this.contextRoot);
		withParameter(config, ConfigurationParameter.COMMAND, this.command);
		withParameter(config, ConfigurationParameter.OUTPUT, this.redirectOutput);
		withParameter(config, ConfigurationParameter.PORT, determinePort());
		withParameter(config, this.fitnesseClasspathVariable, StringUtils.join(classpath, SystemUtils.PATH_SEPARATOR));
		// TODO: still not compatible with fitnesse updates nor install only
		withParameter(config, ConfigurationParameter.OMITTING_UPDATES, true);
		return config;
	}

	private void withParameter(ContextConfigurator config, Object key, Object value) {
		String valueString = Objects.toString(value, null);
		if (StringUtils.isBlank(valueString)) {
			return;
		}
		if (key instanceof ConfigurationParameter) {
			config.withParameter((ConfigurationParameter) key, valueString);
		} else {
			config.withParameter(Objects.toString(key), valueString);
		}
		getLog().debug(String.format("%s: %s", key, valueString));
	}

	private String determineRootPath() {
		if (StringUtils.isNotBlank(this.rootPath)) {
			return Paths.get(this.rootPath).normalize().toString();
		}
		Path basedir = this.project.getBasedir().toPath();
		try {
			Stream<Path> fitNesseRootSearcher = Files.walk(basedir).filter(t -> t.toFile().isDirectory() && t.endsWith(FitnesseRunnerMojo.this.fitNesseRoot));
			return fitNesseRootSearcher.findFirst().get().getParent().toString();
		} catch (NoSuchElementException | IOException e) {
			getLog().debug("Failed to find a valid FitNesseRoot directory under: " + basedir, e);
		}
		return null;
	}

	private int determinePort() {
		return ObjectUtils.defaultIfNull(this.port, determinePort(FitnesseRunnerMojo.PORT_RANGE.getMinimum()));
	}

	private int determinePort(Integer port) {
		if (!FitnesseRunnerMojo.PORT_RANGE.contains(port)) {
			throw new IllegalStateException("Unable to find an available port to run FitNesse within the range: " + FitnesseRunnerMojo.PORT_RANGE);
		}
		try (ServerSocket socket = new ServerSocket(port)) {
			return port;
		} catch (IOException e) {
			return determinePort(port + NumberUtils.INTEGER_ONE);
		}
	}

	private Collection<String> determineClasspath() {
		Map<String, Pair<ArtifactVersion, String>> classpathMap = new HashMap<>();
		collectFileFromArtifacts(classpathMap, this.pluginClasspath);
		Collection<String> classpath = new ArrayList<>();
		if (this.includeProjectDependencies) {
			collectFileFromArtifacts(classpathMap, this.project.getArtifacts());
			classpath.add(this.project.getBuild().getOutputDirectory() + File.separator);
			classpath.add(this.project.getBuild().getTestOutputDirectory() + File.separator);
		}
		classpath.addAll(classpathMap.values().stream().map(versionAndFile -> versionAndFile.getValue()).collect(Collectors.toList()));
		return classpath;
	}

	private void collectFileFromArtifacts(Map<String, Pair<ArtifactVersion, String>> artifactMap, Collection<Artifact> artifacts) {
		artifacts.stream().forEach(artifact -> {
			String artifactKey = String.format("%s%s", artifact.getGroupId(), artifact.getArtifactId());
			Pair<ArtifactVersion, String> entry = artifactMap.get(artifactKey);
			try {
				if (entry == null || entry.getKey().compareTo(artifact.getSelectedVersion()) < NumberUtils.INTEGER_ZERO) {
					artifactMap.put(artifactKey, Pair.of(artifact.getSelectedVersion(), artifact.getFile().getAbsolutePath()));
				}
			} catch (OverConstrainedVersionException e) {
				getLog().warn("Failed to compare artifact versions: " + artifact, e);
			}
		});
	}

	private void execute(ContextConfigurator config, Collection<String> classpath) throws MojoExecutionException {
		try {
			((FitNesseLauncher) ClassUtils.getClass(this.fitNesseLauncherClass).newInstance()).launch(config, classpath);
		} catch (Exception e) {
			throw new MojoExecutionException(e.getMessage(), ExceptionUtils.getRootCause(e));
		}
	}
}
