package batchRunnables;

interface BatchFileGenerator {
	String provideFileContent(FileGenerationData data);
}
