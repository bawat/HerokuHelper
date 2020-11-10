package batchRunnables;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.processing.Filer;
import javax.annotation.processing.FilerException;
import javax.annotation.processing.Messager;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;

public enum BatchRunnablesFactory implements BatchFileGenerator{
	DOWNLOAD_ENVIRONMENT_VARIABLES(){
		@Override
		public String provideFileContent(FileGenerationData data) {
			return
					"@echo off\r\n" + 
					"call heroku config --app $APPNAME >serverEnvVariables.txt\r\n" + 
					"\r\n" + 
					"set \"File=serverEnvVariables.txt\"\r\n" + 
					"set /a count=0\r\n" + 
					"SETLOCAL enabledelayedexpansion\r\n" + 
					"for /F \"tokens=* delims=\" %%a in ('Type \"%File%\"') do (\r\n" + 
					"         Set /a count+=1\r\n" + 
					"         Set \"output[!count!]=%%a\"     \r\n" + 
					")\r\n" + 
					"\r\n" + 
					"For /L %%i in (1,1,%Count%) Do (\r\n" + 
					" Call :ProcessLine \"!output[%%i]!\"\r\n" + 
					")\r\n" + 
					"\r\n" + 
					"del serverEnvVariables.txt\r\n" + 
					"echo Please remember to restart your programs so they accept the new environment variable changes.\r\n" + 
					"pause\r\n" + 
					"Exit\r\n" + 
					"::*******************************************************\r\n" + 
					":ProcessLine\r\n" + 
					"\r\n" + 
					"::Remove spaces from line\r\n" + 
					"set MyVar=%1\r\n" + 
					"set MyVar=%MyVar: =%\r\n" + 
					"\r\n" + 
					"::Check if contains semi colon\r\n" + 
					"if x%MyVar::=%==x%MyVar% exit /b\r\n" + 
					"\r\n" + 
					"::Split line by semi colon into %%a and %%b\r\n" + 
					"for /F \"tokens=1,2 delims=:\" %%a in (%MyVar%) do (\r\n" + 
					"   ::Set our environment variable to the downloaded variable\r\n" + 
					"   setx %%a %%b\r\n" + 
					")\r\n" + 
					"exit /b\r\n" + 
					"::*******************************************************";
		}
	},
	UPLOAD_ENVIRONMENT_VARIABLES(){
		@Override
		public String provideFileContent(FileGenerationData data) {
			if(data.environmentVariables.size() == 0) return "";
			
			String varsToSet = data.environmentVariables.stream()
					.map(var -> var + "=%" + var + "%")
					.collect(Collectors.joining(" "));
			return "heroku config:set " + varsToSet + " --app $APPNAME";
		}
	},
	START_SERVER(){
		@Override
		public String provideFileContent(FileGenerationData data) {
			return "heroku dyno:scale --app $APPNAME worker=1:Free";
		}
	},
	STOP_SERVER(){
		@Override
		public String provideFileContent(FileGenerationData data) {
			return "heroku dyno:scale --app $APPNAME worker=0:Free";
		}
	},
	OPEN_SERVER_LOGS(){
		@Override
		public String provideFileContent(FileGenerationData data) {
			return "heroku logs --app $APPNAME --tail";
		}
	},
	FIRST_TIME_SETUP(){
		@Override
		public String provideFileContent(FileGenerationData data) {
			return ":: Goes into the build/libs folder and creates a Procfile to call java on that JAR file\r\n" + 
					"cd ../build/libs/\r\n" + 
					"FOR /F \"tokens=* USEBACKQ\" %%g IN (`dir /b`) do (SET \"FILE_NAME=%%g\")\r\n" + 
					"cd ../..\r\n" + 
					"echo.worker: java -jar build/libs/%FILE_NAME%>Procfile\r\n" + 
					":: Changes the default gradle task heroku runs to JAR\r\n" + 
					"heroku config:set GRADLE_TASK=\"jar\" --app $APPNAME";
		}
	};
	
	/**
	 * Generates helper .bat files to save developer time.
	 * Generates Enum that provides all environment variables.
	 * @param messager The output stream.
	 * @param filer Our handle used to create files.
	 * @param data Information used to generate the contents of the batch files.
	 * @throws IOException
	 */
	public static void generateAllFiles(Messager messager, Filer filer, FileGenerationData data) throws IOException {
		for(BatchRunnablesFactory each : BatchRunnablesFactory.values()){
			messager.printMessage(Diagnostic.Kind.NOTE, "Generating " + each + ".bat");
			String content = each.provideFileContent(data).replace("$APPNAME", data.herokuAppName);
			writeToBatchFile(messager, filer, each.name() + ".bat", content);
		}
		messager.printMessage(Diagnostic.Kind.NOTE, "Generating Enum for Heroku...");
		generateEnvironmentVariableEnum(messager, filer, data);
	}
	
	/**
	 * Creates a .bat file if it doesn't already exist.
	 * @param messager The output stream.
	 * @param filer Our handle used to create files.
	 * @param fileName Name of the file to create.
	 * @param fileContents The contents of the file.
	 * @throws IOException
	 */
	private static void writeToBatchFile(Messager messager, Filer filer, String fileName, String fileContents) throws IOException {
		FileObject file = null;
		try {
			file = filer.createResource(StandardLocation.SOURCE_OUTPUT, "", fileName);
		} catch(FilerException e) {
			messager.printMessage(Diagnostic.Kind.NOTE, "Batch already Generated. Skipping.");
			return;
		}
		Writer writer = file.openWriter();
		PrintWriter out = new PrintWriter(writer);
		out.write(fileContents);
		out.close();
		writer.close();
	}
	
	/**
	 * Generate Enumeration to check if all environment variables are present. Enumeration supplies the environment variables' real values to the user. 
	 * @param messager The output stream.
	 * @param filer Our handle used to create files.
	 * @param data Information used to generate the contents of the batch files.
	 * @throws IOException
	 */
	private static void generateEnvironmentVariableEnum(Messager messager, Filer filer, FileGenerationData data) throws IOException {
		final String packageName = "heroku";
		
		//Generate the file
		JavaFileObject enumFile = null;
		try {
			enumFile = filer.createSourceFile(packageName + ".HerokuEnvironmentVariables");
		} catch(FilerException e) {
			messager.printMessage(Diagnostic.Kind.NOTE, "Enum already Generated. Skipping.");
			return;
		}
		Writer writer = enumFile.openWriter();
		PrintWriter out = new PrintWriter(writer);
		
		//Ensure the enum names are legal
		Function<String, String> formatStringAsEnum = str -> str.toUpperCase().chars()
			.map(ascii -> {
				if(ascii >= 48 && ascii <= 57) return ascii;// 0..9
				if(ascii >= 65 && ascii <= 90) return ascii;// A..Z
				return (int)'_';
			}).mapToObj(ascii -> String.valueOf((char) ascii)).collect(Collectors.joining());
		
		//Place content in the file
		String toWrite = ""
				+ "package " + packageName + ";\r\n"
				+ "public enum HerokuEnvironmentVariables{" + System.lineSeparator();
		toWrite += data.environmentVariables.stream().map(varName -> "	" + formatStringAsEnum.apply(varName) + "(\""+varName+"\")").collect(Collectors.joining(","+System.lineSeparator())) + ";";
		toWrite += "\r\n" +
				"	private String variableName;\r\n" + 
				"	HerokuEnvironmentVariables(String variableName){\r\n" + 
				"		this.variableName = variableName;\r\n" + 
				"	}\r\n" + 
				"	\r\n" + 
				"	public String getEnvVar() {\r\n" + 
				"		return System.getenv(variableName);\r\n" + 
				"	}\r\n";
		
		toWrite += "\r\n" +
				"	static {\r\n" + 
				"		String missingVariables = \"\";\r\n" + 
				"		for(HerokuEnvironmentVariables value : HerokuEnvironmentVariables.values()) {\r\n" + 
				"			String result = System.getenv(value.variableName);\r\n" + 
				"			if(result == null) {\r\n" + 
				"				missingVariables += \"Environment variable \" + value.variableName + \" not present on local machine.\" + System.lineSeparator();\r\n" + 
				"			}\r\n" + 
				"		}\r\n" + 
				"		if(!missingVariables.isEmpty()) throw new RuntimeException(missingVariables);\r\n" + 
				"	}\r\n"; 
		
		toWrite += "}";
		
		out.write(toWrite);
		out.close();
		writer.close();
	}
}
