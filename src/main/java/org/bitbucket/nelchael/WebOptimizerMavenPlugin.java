package org.bitbucket.nelchael;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo(name = "web-optimizer", defaultPhase = LifecyclePhase.GENERATE_RESOURCES)
@SuppressWarnings("Convert2Lambda")
public class WebOptimizerMavenPlugin extends AbstractMojo {
	@Parameter(name = "uglifyjs-binary")
	private String uglifyjsBinary;
	@Parameter(name = "uglifyjs-options", defaultValue = "-c -m --stats")
	private String uglifyjsOptions;
	@Parameter(name = "cleancss-binary")
	private String cleancssBinary;
	@Parameter(name = "cleancss-options", defaultValue = "-d")
	private String cleancssOptions;
	@Parameter(name = "output-directory", defaultValue = "${project.basedir}/target/${project.build.finalName}/")
	private File outputDirectory;
	@Parameter(name = "js-source-directory", defaultValue = "${project.basedir}/src/main/javascript/")
	private File jsSourceDirectory;
	@Parameter(name = "js-source-files")
	private String[] jsSourceFiles;
	@Parameter(name = "js-output-name", defaultValue = "app.js")
	private String jsOutputName;
	@Parameter(name = "css-source-directory", defaultValue = "${project.basedir}/src/main/css/")
	private File cssSourceDirectory;
	@Parameter(name = "css-source-files")
	private String[] cssSourceFiles;
	@Parameter(name = "css-output-name", defaultValue = "app.css")
	private String cssOutputName;

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
			BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
			while (process.isAlive()) {
				if (reader.ready()) {
					processOutput.append(reader.readLine()).append("\n");
				}
			}
			reader.close();

			long processDuration = System.currentTimeMillis() - processStart;

			if (process.exitValue() != 0) {
				getLog().error(processOutput.toString());
				throw new MojoExecutionException(command + " failed with exit code " + process.exitValue());
			} else {
				getLog().info(command + " completed in " + processDuration + "ms");
				getLog().debug("-- " + command + " output --");
				getLog().debug(processOutput.toString().trim());
				getLog().debug("-- end " + command + " output --");
			}
		} catch (IOException e) {
			throw new MojoExecutionException(command + " failed", e);
		}
	}

	private List<String> makeListOfInputFiles(File sourceDirectory, String[] inputFiles) throws MojoExecutionException {
		List<String> inputFileFiles = new ArrayList<>();
		for (String fileName : inputFiles) {
			File file = new File(sourceDirectory, fileName);
			if (file.exists()) {
				inputFileFiles.add(file.getAbsolutePath());
			} else {
				getLog().warn(file + " does not exist - skipping it");
			}
		}

		return inputFileFiles;
	}

	private String checkOutputDirectory(String childFileName) throws MojoExecutionException {
		File finalOutputFile = new File(outputDirectory, childFileName);
		File finalOutputDirectory = finalOutputFile.getParentFile();
		getLog().debug("Output directory: " + finalOutputDirectory);
		if (!finalOutputDirectory.exists()) {
			if (!finalOutputDirectory.mkdirs()) {
				throw new MojoExecutionException("Failed to create directory " + finalOutputDirectory);
			}
		}
		return finalOutputFile.getAbsolutePath();
	}

	private void processJavaScript() throws MojoExecutionException {
		String outputFileName = checkOutputDirectory(jsOutputName);
		getLog().info("Processing JavaScript files (using " + uglifyjsBinary + ") to " + outputFileName);

		List<String> inputs = makeListOfInputFiles(jsSourceDirectory, jsSourceFiles);
		if (inputs.isEmpty()) {
			getLog().info("No input files, skipping uglifyjs run");
			return;
		}

		List<String> uglifyJsArguments = new ArrayList<>();
		if (StringUtils.isNotBlank(uglifyjsOptions)) {
			uglifyJsArguments.addAll(Arrays.asList(uglifyjsOptions.split("[ \t]+")));
		}
		uglifyJsArguments.add("-o");
		uglifyJsArguments.add(outputFileName);
		uglifyJsArguments.addAll(inputs);
		runProcess(uglifyjsBinary, uglifyJsArguments);
	}

	private void processCss() throws MojoExecutionException {
		String outputFileName = checkOutputDirectory(cssOutputName);
		getLog().info("Processing CSS files (using " + cleancssBinary + ") to " + outputFileName);

		List<String> inputs = makeListOfInputFiles(cssSourceDirectory, cssSourceFiles);
		if (inputs.isEmpty()) {
			getLog().info("No input files, skipping cleancss run");
			return;
		}

		List<String> cleanCssArguments = new ArrayList<>();
		if (StringUtils.isNotBlank(cleancssOptions)) {
			cleanCssArguments.addAll(Arrays.asList(cleancssOptions.split("[ \t]+")));
		}
		cleanCssArguments.add("-o");
		cleanCssArguments.add(outputFileName);
		cleanCssArguments.addAll(inputs);
		runProcess(cleancssBinary, cleanCssArguments);
	}

	public void execute() throws MojoExecutionException {
		if (uglifyjsBinary == null) {
			if (System.getProperty("os.name").toLowerCase(Locale.getDefault()).contains("windows")) {
				uglifyjsBinary = "uglifyjs.cmd";
			} else {
				uglifyjsBinary = "uglifyjs";
			}
		}
		if (cleancssBinary == null) {
			if (System.getProperty("os.name").toLowerCase(Locale.getDefault()).contains("windows")) {
				cleancssBinary = "cleancss.cmd";
			} else {
				cleancssBinary = "cleancss";
			}
		}

		if (jsSourceDirectory.exists()) {
			processJavaScript();
		} else {
			getLog().info("JavaScript source directory (" + jsSourceDirectory + ") does not exist - skipping uglify run");
		}
		if (cssSourceDirectory.exists()) {
			processCss();
		} else {
			getLog().info("CSS source directory (" + cssSourceDirectory + ") does not exist - skipping cleancss run");
		}
	}
}
