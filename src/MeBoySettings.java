
public class MeBoySettings {

	//preparations for premium version
	public static boolean isPremium = true;
	public static String version = "1.2.0";
	public final static boolean isAsha = true;
	
	public static String getVersionString(){
		if(isPremium){
			return "MeBoy Premium " + version;
		}else{
			return "MeBoy Lite " + version;
		}
	}
	
	public static boolean isSharingSupported(){
		if(isAsha == false)
			return false;
		String platform = System.getProperty("microedition.platform");
		if(platform.indexOf("Nokia_Asha_1_1") > 0)
			return true;
		else
			return false;
	}
	
}
