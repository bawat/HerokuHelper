package batchRunnables;

import java.util.Collection;

public class FileGenerationData {
	
	final Collection<String> environmentVariables;
	final String herokuAppName;
	public FileGenerationData(Collection<String> environmentVariables, String herokuAppName) {
		this.environmentVariables = environmentVariables;
		this.herokuAppName = herokuAppName;
	}
}
