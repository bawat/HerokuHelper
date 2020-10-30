package ann;
import java.lang.annotation.*;

/**
 * Optional. Used to specify the names of environment variables that should be present on both local machine and server.
 * Will generate a runtime error if one of these declared environment variables isn't present.
 * @author bawat
 */
@Repeatable(value = UploadToHeroku.Multiple.class)
@Retention(RetentionPolicy.CLASS)
public @interface UploadToHeroku {
	String environmentVariableName();
	
	public @interface Multiple {
		UploadToHeroku[] value();
	}
}