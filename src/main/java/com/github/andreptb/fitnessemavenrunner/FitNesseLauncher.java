
package com.github.andreptb.fitnessemavenrunner;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collection;
import java.util.HashSet;

import org.apache.commons.lang3.math.NumberUtils;

import fitnesse.ContextConfigurator;
import fitnesseMain.FitNesseMain;

public class FitNesseLauncher {

	public void launch(ContextConfigurator config, Collection<String> classpath) throws Exception {
		Integer result = null;
		Collection<Thread> previousThreadsSnapshot = new HashSet<>(Thread.getAllStackTraces().keySet());
		URL[] classpathUrl = new URL[classpath.size()];
		int i = 0;
		for (String entry : classpath) {
			classpathUrl[i++] = new File(entry).toURI().toURL();
		}
		Thread.currentThread().setContextClassLoader(new URLClassLoader(classpathUrl, getClass().getClassLoader()));
		result = new FitNesseMain().launchFitNesse(config);
		// no result means web execution mode
		if (result == null) {
			Thread.getAllStackTraces().keySet().stream().filter(thread -> !previousThreadsSnapshot.contains(thread)).findFirst().get().join();
			return;
		}
		if (result > NumberUtils.INTEGER_ZERO) {
			throw new IllegalStateException(result + " FitNesse tests failed to execute");
		}
	}
}
