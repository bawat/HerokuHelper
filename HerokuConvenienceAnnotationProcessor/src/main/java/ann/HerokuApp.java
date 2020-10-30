package ann;
import java.lang.annotation.*;

/***
 * Required. The app name, used to run various heroku commands.
 * @author bawat
 */
@Retention(RetentionPolicy.CLASS)
public @interface HerokuApp {
	String herokuAppName();
}