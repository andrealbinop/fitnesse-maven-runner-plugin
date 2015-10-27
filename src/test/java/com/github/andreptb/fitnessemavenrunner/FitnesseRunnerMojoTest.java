
package com.github.andreptb.fitnessemavenrunner;

import java.io.File;
import java.util.HashMap;
import java.util.Objects;

import org.apache.maven.plugin.testing.MojoRule;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class FitnesseRunnerMojoTest {

	@Rule
	public MojoRule rule = new MojoRule();
	private MojoExecutor mojo;

	@Before
	public void setUpMavenProject() throws Exception {
		this.mojo = new MojoExecutor();
	}

	@Test
	public void testRunSuiteWithDefaultConfig() throws Exception {
		this.mojo.put("command", "FrontPage?suite&format=text");
		this.mojo.execute("/test-project", "run");
	}

	private class MojoExecutor extends HashMap<String, String> {

		void execute(String pom, String goal) throws Exception {
			Xpp3Dom[] config = entrySet().stream().map(entry -> {
				Xpp3Dom dom = new Xpp3Dom(Objects.toString(entry.getKey()));
				dom.setValue(Objects.toString(entry.getValue()));
				return dom;
			}).toArray(size -> new Xpp3Dom[size]);
			FitnesseRunnerMojoTest.this.rule.executeMojo(FitnesseRunnerMojoTest.this.rule.readMavenProject(new File(getClass().getResource(pom).toURI())), goal, config);
		}
	}
}
