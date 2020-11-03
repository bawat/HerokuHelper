package proc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.annotation.processing.*;
import javax.lang.model.*;
import javax.lang.model.element.*;
import javax.tools.*;

import com.google.auto.service.*;

import ann.HerokuApp;
import ann.UploadToHeroku;
import batchRunnables.BatchRunnablesFactory;
import batchRunnables.FileGenerationData;

@AutoService(Processor.class)
public class MyProcessor extends AbstractProcessor {
	
	//Helper method to make sure the EnvironmentVariable list remains unique
	private <T> void addIfNotExists(Collection<T> container, T value) {
		if(container.contains(value)) return;
		container.add(value);
	}
	
	@Override
	public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
		Messager messager = processingEnv.getMessager();
		Collection<String> environmentVariablesToUpload = new ArrayList<String>();
		String herokuAppName = null;
		
		boolean didIProcessThemAll = true;//I think this is for use with the annotations parameter?
		
		//Collect all of the environment variables from our annotations, so that we can use them to generate batch files and check their presence.
		for(Element ele : roundEnv.getElementsAnnotatedWith(UploadToHeroku.class)) {
			addIfNotExists(environmentVariablesToUpload, ele.getAnnotation(UploadToHeroku.class).environmentVariableName());
		}
		for(Element ele : roundEnv.getElementsAnnotatedWith(UploadToHeroku.Multiple.class)) {
			for(UploadToHeroku ann : ele.getAnnotation(UploadToHeroku.Multiple.class).value()) {
				addIfNotExists(environmentVariablesToUpload, ann.environmentVariableName());
			}
		}
		
		//Collect the name of our Heroku app.
		for(Element ele : roundEnv.getElementsAnnotatedWith(HerokuApp.class)) {
			herokuAppName = ele.getAnnotation(HerokuApp.class).herokuAppName();
		}
		
		//If user has specified the name of an environment variable, they will need to specify what Heroku app this project refers to.
		if(herokuAppName == null){
			if(environmentVariablesToUpload.isEmpty()) {
				return didIProcessThemAll;
			}else {
				messager.printMessage(Diagnostic.Kind.ERROR, "To use @UploadToHeroku, at least one @HerokuApp annotation needs to be present.");
				return didIProcessThemAll;
			}
		}
		
		//Generate .bat helper files and environment variable helper Enum.
		messager.printMessage(Diagnostic.Kind.NOTE, "Generating Heroku helper files...");
		try {
			BatchRunnablesFactory.generateAllFiles(messager, processingEnv.getFiler(), new FileGenerationData(environmentVariablesToUpload, herokuAppName));
			messager.printMessage(Diagnostic.Kind.NOTE, "Generated.");
		}catch(Exception e){
			messager.printMessage(Diagnostic.Kind.ERROR, e.getMessage() + " " + e.getStackTrace());
		}
		
		return didIProcessThemAll;
	}

	@Override
	public Set<String> getSupportedAnnotationTypes() {
	    Set<String> annotataions = new LinkedHashSet<String>();
	    annotataions.add(UploadToHeroku.class.getCanonicalName());
	    annotataions.add(UploadToHeroku.Multiple.class.getCanonicalName());
	    annotataions.add(HerokuApp.class.getCanonicalName());
	    return annotataions;
	}

	@Override
	public SourceVersion getSupportedSourceVersion() {
	    return SourceVersion.latestSupported();
	}
}