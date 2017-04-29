package org.bitbucket.nelchael;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

@Mojo(name = "web-helper", defaultPhase = LifecyclePhase.COMPILE)
@SuppressWarnings("Convert2Lambda")
public class WebHelperMavenPlugin extends AbstractMojo {
	@Parameter(property = "project")
	private MavenProject project;
	@Parameter(defaultValue = "false")
	private boolean cssRebase;

	private List<File> findSourceFiles(File root, String extension) throws IOException {
		return Files.walk(root.toPath()).filter(new Predicate<Path>() {
			@Override
			public boolean test(Path path) {
				return path.toFile().isFile() && path.toString().endsWith(extension);
			}
		}).map(new Function<Path, File>() {
			@Override
			public File apply(Path path) {
				return path.toFile();
			}
		}).sorted(new Comparator<File>() {
			@Override
			public int compare(File o1, File o2) {
				int o1Len = o1.toPath().getNameCount();
				int o2Len = o2.toPath().getNameCount();
				return o1Len == o2Len ? o1.compareTo(o2) : o2Len - o1Len;
			}
		}).collect(Collectors.toList());
	}

	private void runProcess(String command, List<String> arguments) throws MojoExecutionException {
		getLog().debug("Running " + command + " with arguments: ");
		for (String string : arguments) {
			getLog().debug("\t" + string);
		}
		List<String> finalCommandLine = new ArrayList<>();
		finalCommandLine.add(command);
		finalCommandLine.addAll(arguments);
		StringBuilder processOutput = new StringBuilder();
		try {
			long processStart = System.currentTimeMillis();
			Process process = new ProcessBuilder(finalCommandLine).redirectErrorStream(true).start();
			process.waitFor();
			BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
			reader.lines().forEach(new Consumer<String>() {
				@Override
				public void accept(String s) {
					processOutput.append(s).append("\n");
				}
			});
			long processDuration = System.currentTimeMillis() - processStart;
			if (process.exitValue() != 0) {
				getLog().error(processOutput.toString());
				throw new MojoExecutionException(command + " failed with exit code " + process.exitValue());
			} else {
				getLog().info(command + " completed in " + processDuration + "ms");
				getLog().debug(processOutput.toString());
			}
		} catch (IOException | InterruptedException e) {
			throw new MojoExecutionException(command + " failed", e);
		}
	}

	private void processJavaScript(File javaScriptInputDirectory, File outputDirectory) throws MojoExecutionException {
		List<File> javaScriptInputs;
		try {
			javaScriptInputs = findSourceFiles(javaScriptInputDirectory, ".js");
		} catch (IOException e) {
			throw new MojoExecutionException("Failed to find JavaScript source files", e);
		}

		List<String> uglifyJsArguments = new ArrayList<>();
		uglifyJsArguments.addAll(javaScriptInputs.stream().map(new Function<File, String>() {
			@Override
			public String apply(File file) {
				return file.toString();
			}
		}).collect(Collectors.toList()));
		uglifyJsArguments.add("-c");
		uglifyJsArguments.add("-m");
		uglifyJsArguments.add("-o");
		uglifyJsArguments.add(new File(outputDirectory, "app.js").toString());
		runProcess("uglifyjs.cmd", uglifyJsArguments);
	}

	private void processCss(File cssInputDirectory, File outputDirectory) throws MojoExecutionException {
		List<File> cssInputs;
		try {
			cssInputs = findSourceFiles(cssInputDirectory, ".css");
		} catch (IOException e) {
			throw new MojoExecutionException("Failed to find CSS source files", e);
		}

		List<String> cleanCssArguments = new ArrayList<>();
		if (!cssRebase) {
			cleanCssArguments.add("--skip-rebase");
		}
		cleanCssArguments.add("-o");
		cleanCssArguments.add(new File(outputDirectory, "app.css").toString());
		cleanCssArguments.addAll(cssInputs.stream().map(new Function<File, String>() {
			@Override
			public String apply(File file) {
				return file.toString();
			}
		}).collect(Collectors.toList()));
		runProcess("cleancss.cmd", cleanCssArguments);
	}

	public void execute() throws MojoExecutionException {
		File javaScriptInputDirectory = new File(project.getBasedir(), "src/main/javascript");
		File cssInputDirectory = new File(project.getBasedir(), "src/main/css");
		File outputDirectory = new File(project.getBasedir(), "target/" + project.getBuild().getFinalName() + "/assets");

		getLog().info("JavaScript: " + javaScriptInputDirectory);
		getLog().info("CSS: " + cssInputDirectory);
		getLog().info("Output: " + outputDirectory);

		if (!outputDirectory.exists()) {
			if (!outputDirectory.mkdirs()) {
				throw new MojoExecutionException("Failed to create directory " + outputDirectory);
			}
		}

		processJavaScript(javaScriptInputDirectory, outputDirectory);
		processCss(cssInputDirectory, outputDirectory);
	}
}
