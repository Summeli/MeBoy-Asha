
import java.util.Hashtable;

import javax.microedition.io.file.FileSystemRegistry;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.ImageItem;
import javax.microedition.lcdui.List;
import javax.microedition.lcdui.StringItem;
import javax.microedition.location.Location;
import javax.microedition.location.LocationProvider;
import javax.microedition.location.QualifiedCoordinates;

import com.nokia.mid.location.LocationUtil;

import InneractiveSDK.IADView;
import InneractiveSDK.IADView.IaOptionalParams;
import InneractiveSDK.InneractiveAdEventsListener;

public class AdScreen extends Form implements CommandListener, InneractiveAdEventsListener{
	
    private final MeBoy parent;
 
    LocationProvider provider;
    Location location;
    QualifiedCoordinates coordinates = null;
    
    
	public AdScreen(MeBoy parent) {
		super("MeBoy Starting");
		this.parent = parent;
		setCommandListener(this);
		addCommand(new Command("Skip Add", Command.BACK, 1));
	}

	public void commandAction(Command c, Displayable arg1) {
		if (c.getCommandType() == Command.BACK) {
			parent.adExit();
		}
	}
	
	public void showAdd(){
		try {
            //Specify the retrieval method to Online/Cell-ID
            int[] methods = {(Location.MTA_ASSISTED | Location.MTE_CELLID | Location.MTE_SHORTRANGE | Location.MTY_NETWORKBASED)};
            // Retrieve the location provider
            provider = LocationUtil.getLocationProvider(methods, null);
            location=provider.getLocation(50000);
            coordinates=location.getQualifiedCoordinates();
        } catch (Exception e){
        	
        }
		if(coordinates != null){
			//we have coordinages, lets use them for ads
			Hashtable metaData = new Hashtable();
			String coordinateMeta = coordinates.getLatitude() + ","+ coordinates.getLongitude();
			metaData.put(IaOptionalParams.Key_Gps_Location,coordinateMeta);
			IADView.displayInterstitialAd(parent, "AnttiPohjola_MeBoy_Nokia", metaData,this);
		}else{
			IADView.displayInterstitialAd(parent, "AnttiPohjola_MeBoy_Nokia", this);
		}
	}

	//Add listeners
	public void inneractiveOnClickAd() {
		
	}
	
	public void inneractiveOnSkipAd() {
		parent.adExit();
	}

	public void inneractiveOnFailedToReceiveAd() {
	}


	public void inneractiveOnReceiveAd() {	
	}

	public void inneractiveOnReceiveDefaultAd() {
	}
}
