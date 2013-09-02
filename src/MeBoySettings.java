
public class MeBoySettings {

	//preparations for premium version
	public final static boolean isPremium = true;
	public final static String version = "1.0.0";
	public final static boolean isAsha = true;
	
	public static String getVersionString(){
		if(isPremium){
			return "MeBoy Premium " + version;
		}else{
			return "MeBoy Lite " + version;
		}
	}
}
