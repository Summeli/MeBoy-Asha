/*

MeBoy

Copyright 2005-2009 Bjorn Carlin
http://www.arktos.se/

Based on JavaBoy, COPYRIGHT (C) 2001 Neil Millstone and The Victoria
University of Manchester. Bluetooth support based on code contributed by
Martin Neumann.

This program is free software; you can redistribute it and/or modify it
under the terms of the GNU General Public License as published by the Free
Software Foundation; either version 2 of the License, or (at your option)
any later version.

This program is distributed in the hope that it will be useful, but WITHOUT
ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
more details.


You should have received a copy of the GNU General Public License along with
this program; if not, write to the Free Software Foundation, Inc., 59 Temple
Place - Suite 330, Boston, MA 02111-1307, USA.

*/

import java.io.*;
import java.util.*;

import javax.microedition.content.Invocation;
import javax.microedition.content.Registry;
import javax.microedition.lcdui.*;
import javax.microedition.midlet.*;
import javax.microedition.rms.*;


import com.nokia.mid.ui.*;


/**
 * The main class offers a list of games, read from the file "carts.txt". It
 * also handles logging.
 */
public class MeBoy extends MIDlet implements CommandListener {
	// Settings, etc.
	public static final boolean debug = false;
	public static final int rotations = 1;
	public static int maxFrameSkip = 3;
	public final static boolean enableScaling = true;
	public final static int scalingMode = 0;
	public static boolean keepProportions = true;
	public final static boolean fullScreen = true;
	public static final boolean disableColor = false;
	public static boolean enableSound = false;
	public final static boolean advancedSound = false;
	public final static boolean advancedGraphics = false;
	public static boolean showFps = false;
	public static boolean showLogItem = false;
	public static int lazyLoadingThreshold = 20; // number of banks, each 0x4000 bytes = 16kB
	public static int language;
	public static int suspendCounter = 1; // next index for saved games
	public static String[] suspendName10 = new String[0]; // v1-style suspended games
	public static String[] suspendName20 = new String[0]; // v2-style suspended games
	public static String[] literal = new String[80];
	private static int languageCount = 1;
	private static String[] languages = new String[] {"English"}; // if index.txt fails
	private static int[] languageLookup = new int[] {0};
	private String[] cartDisplayName = null;
	private String[] cartFileName = null;
	private String[] cartID = null;
	private int numCarts;
	private boolean fatalError;
	
	
	public static String logString = "";
	private static MeBoy instance;
	
	// UI components
	public static Display display;
	
	private List mainMenu;
	private Form messageForm;
	private GBCanvas gbCanvas;
	private List cartList;
	private List suspendList;
	
	// Settings
	private Form settingsForm;
	private ChoiceGroup graphicsGroup;
	private TextField loadThresholdField;
	private ChoiceGroup soundGroup;
	private ChoiceGroup languageGroup;
	
	private FileSelector fileSelector;
	private AdScreen adscreen;
	
	//sharing
	private Registry registry;
	private String romName = null;
	public void startApp() {
		if (instance == this) {
			return;
		}
		
		instance = this;
		display = Display.getDisplay(this);

		GBCanvas.readSettings();

		readLangIndexFile();
		
		if (!readLiteralsFile())
			return;
		/*
		if (!readCartNames())
			return;*/
		/*if (!upgradeSavegames())
			return;
*/

		fileSelector = new FileSelector(this);
		if( MeBoySettings.isPremium == false ){
			adscreen = new AdScreen(this);
			adscreen.showAdd();
		}else{
			showMainMenu();
		}
		
	}



	private boolean readLiteralsFile() {
		try {
			InputStreamReader isr = new InputStreamReader(getClass().getResourceAsStream("/lang/" + language + ".txt"), "UTF-8");
			int counter = 0;
			String s;
			while ((s = next(isr)) != null)
				literal[counter++] = s;
			isr.close();
			while (counter < literal.length)
				literal[counter++] = "?";
		} catch (Exception e) {
			e.printStackTrace();
			if (language > 0) {
				language = 0;
				return readLiteralsFile();
			}
			
			showError("Failed to read the language file.", null, e);
			fatalError = true;
			return false;
		}
		return true;
	}

	private void readLangIndexFile() {
		try {
			InputStreamReader isr = new InputStreamReader(getClass().getResourceAsStream("/lang/index.txt"), "UTF-8");
			
			String s;
			languageLookup = new int[20];
			Vector langVector = new Vector();
			languageCount = 0;
			while ((s = next(isr)) != null) {
				languageLookup[languageCount++] = s.charAt(0) - 'a';
				langVector.addElement(next(isr));
			}
			languages = new String[languageCount];
			langVector.copyInto(languages);
			
			isr.close();
		} catch (Exception e) {
			languages = new String[] {"English"};
			languageLookup = new int[] {0};
			languageCount = 1;
			
			if (debug) {
				e.printStackTrace();
			}
		}
	}
	
	private String next(InputStreamReader isr) throws IOException {
		StringBuffer sb = new StringBuffer();
		int c;
		while ((c = isr.read()) != -1) {
			if (c >= 32) {
				sb.append((char) c);
			} else if (sb.length() > 0) {
				return sb.toString();
			}
		}
		if (sb.length() > 0) {
			return sb.toString();
		}
		return null;
	}
	

	public void pauseApp() {
		if (gbCanvas != null) {
			gbCanvas.pause();
		}
	}

	public void destroyApp(boolean unconditional) {
		  fileSelector.stop();
	}

	public void unloadCart() {
		showMainMenu();
		gbCanvas.releaseReferences();
		gbCanvas = null;
	}
	
	private static String formatDetails(String details, Throwable exception) {
		if (debug && exception != null) {
			exception.printStackTrace();
		}
		
		if (exception != null) {
			if (details == null || details.length() == 0)
				details = exception.toString();
			else
				details += ", " + exception;
		}
		if (details != null && details.length() > 0)
			return " (" + details + ")";
		return "";
	}
	
	public static void showError(String message, String details, Throwable exception) {
		if (message == null)
			message = literal[47];
		instance.showMessage(literal[46], message + formatDetails(details, exception));
		if (instance.gbCanvas != null)
			instance.gbCanvas.releaseReferences();
		instance.gbCanvas = null;
	}

	public static void showLog() {
		instance.showMessage(literal[28], logString);
	}
	
	public void showMessage(String title, String message) {
		messageForm = new Form(title);
		messageForm.append(message);
		messageForm.setCommandListener(this);
		messageForm.addCommand(new Command(literal[10], Command.BACK, 0));
		display.setCurrent(messageForm);
	}
	
	private void messageCommand() {
		if (fatalError) {
			destroyApp(true);
			notifyDestroyed();
		} else {
			messageForm = null;
			showMainMenu();
		}
	}

	private void showMainMenu() {
		mainMenu = new List(MeBoySettings.getVersionString(), List.IMPLICIT);
		//if game is paused, first item should be resume
		if(gbCanvas != null && gbCanvas.isPaused()){
			mainMenu.append(literal[1], null);
		}
		
		mainMenu.append(literal[0], null);
		if (suspendName20.length > 0) {
			mainMenu.append(literal[1], null);
		}
		
		mainMenu.append(literal[2], null);
		mainMenu.append("Instructions", null);
		mainMenu.append(literal[5], null);
		if(romName != null && MeBoySettings.isSharingSupported())
			mainMenu.append("Share", null);
		if (showLogItem) {
			mainMenu.append(literal[3], null);
		}
		if(MeBoySettings.isAsha == false){
			mainMenu.append(literal[6], null);
		}
		mainMenu.setCommandListener(this);
		display.setCurrent(mainMenu);
	}
	private void mainMenuCommand(Command com) {
		String item = mainMenu.getString(mainMenu.getSelectedIndex());
		if (item == literal[0]) {
				if(MeBoySettings.isAsha == true){
					fileSelector.showAshaFileSelectionDialog();
				}else{
					if(fileSelector.initialize()){
						display.setCurrent(fileSelector);
					}else{
						showNoFileAccessMessage();
					}
				}
		} else if (item == literal[1]) {
			resumeGame();
			//showResumeGame();
		} else if (item == literal[2]) {
			showSettings();
		}  else if (item == literal[4]) {
			showMessage("Error:","Not Implemented");
		}else if (item == literal[5]) {
			showMessage(MeBoySettings.getVersionString(), MeBoySettings.getVersionString() + " for S40 and Nokia Aha \n" +
					                 "by: Antti Pohjola, summeli@summeli.fi \nhttp://www.summeli.fi\n"+
					                 "MeBoy is licenced under GPLv2 licence \n" +
					                 "You can get the source code from: http://github.com/Summeli/Meboy-Asha \n\n"+
					                 "Meboy was originally developed for j2ME by: Björn Carlin, 2005-2009.\nhttp://arktos.se/meboy/ \n\n"+
					                 "LEGAL: This product is not affiliated with, not authorized, endorsed or licensed in any way by Nintendo Corporation, its affiliates or subsidiaries.");
		} else if (item == literal[3]) {
			log(literal[29] + " " + Runtime.getRuntime().freeMemory() + "/" + Runtime.getRuntime().totalMemory());
			showLog();
		} else if (item == literal[6]) {
			destroyApp(true);
			notifyDestroyed();
		}else if( item == "Instructions"){
			String enterMenu = "To enter back to the menu from the game screen press the small triangle on the center of the bottom screen\n\n";
			//if Asha
			//enterMenu ="To enter back to the manu from the gamescreen press back button\n\n";
			showMessage("Instructions", "This app is a GameBoy emulator. Copy your gameboy rom files (.gb and .gbc) into the phone with PC.\n" +
					"Then open this app, and press load new game, and load the new ROM. \n\n" + enterMenu +
					"Enjoy playing!;-)");
		}else if (item == "Share"){
			startShare();
		}else {
			showError(null, "Unknown command: " + com.getLabel(), null);
		}
	}

	private void cartListCommand(Command com) {
		if (com.getCommandType() == Command.BACK) {
			cartList = null;
			showMainMenu();
			return;
		}
		
		int ix = cartList.getSelectedIndex();
		String selectedCartID = cartID[ix];
		String selectedCartDisplayName = cartDisplayName[ix];
		try {
			gbCanvas = new GBCanvas(selectedCartID, this, selectedCartDisplayName);
			cartList = null;
			display.setCurrent(gbCanvas);
		} catch (Exception e) {
			showError(null, "error#1", e);
		}
	}

	private void showResumeGame() {
		if (suspendName20.length == 0) {
			showMainMenu();
			return;
		}
		suspendList = new List(literal[7], List.IMPLICIT);

		for (int i = 0; i < suspendName20.length; i++) {
			suspendList.append(suspendName20[i], null);
		}
		suspendList.addCommand(new Command(literal[8], Command.SCREEN, 2));
		suspendList.addCommand(new Command(literal[9], Command.SCREEN, 2));
		suspendList.addCommand(new Command(literal[10], Command.BACK, 1));
		suspendList.setCommandListener(this);
		display.setCurrent(suspendList);
	}

	private void resumeGameCommand(Command com) {
		if (com.getCommandType() == Command.BACK) {
			suspendList = null;
			showMainMenu();
			return;
		}
		
		String label = com.getLabel();
		int index = suspendList.getSelectedIndex();
		String selectedName = suspendName20[index];
		
		if (label == literal[8]) {
			try {
				// update index:
				String[] oldIndex = suspendName20;
				suspendName20 = new String[oldIndex.length - 1];
				System.arraycopy(oldIndex, 0, suspendName20, 0, index);
				System.arraycopy(oldIndex, index + 1, suspendName20, index,
						suspendName20.length - index);
				GBCanvas.writeSettings();

				// delete the state itself
				RecordStore.deleteRecordStore("20S_" + selectedName);
			} catch (Exception e) {
				showError(null, "error#2", e);
			}
			showResumeGame();
		} else if (label == literal[9]) {
			try {
				String oldName = selectedName;
				String newName = suspendCounter++ + oldName.substring(oldName.indexOf(':'));

				RecordStore rs = RecordStore.openRecordStore("20S_" + oldName,
						true);
				byte[] b1 = rs.getRecord(1); // cartid
				byte[] b2 = rs.getRecord(2); // data
				rs.closeRecordStore();

				rs = RecordStore.openRecordStore("20S_" + newName, true);
				rs.addRecord(b1, 0, b1.length);
				rs.addRecord(b2, 0, b2.length);
				rs.closeRecordStore();

				addSuspendedGame(newName);
				showResumeGame();
			} catch (Exception e) {
				showError(null, "error#3", e);
			}
		} else {
			try {
				String suspendName = selectedName;
				RecordStore rs = RecordStore.openRecordStore("20S_" + suspendName, false);
				String suspendCartID = new String(rs.getRecord(1));
				byte[] suspendState = rs.getRecord(2);
				rs.closeRecordStore();
				String suspendCartDisplayName = null;
				for (int i = 0; i < numCarts; i++)
					if (suspendCartID.equals(cartID[i]))
						suspendCartDisplayName = cartDisplayName[i];
				if (suspendCartDisplayName == null) {
					showError(literal[11], suspendCartID, null);
				} else {
					gbCanvas = new GBCanvas(suspendCartID, this, suspendCartDisplayName,
										suspendName, suspendState);
					display.setCurrent(gbCanvas);
				}
				suspendList = null;
			} catch (Exception e) {
				showError(null, "error#4", e);
			}
		}
	}

	private void showSettings() {
		settingsForm = new Form(literal[2]);

		graphicsGroup = new ChoiceGroup(literal[14], ChoiceGroup.MULTIPLE,
				new String[]{literal[33]}, null);
		graphicsGroup.setSelectedIndex(0, showFps);
		settingsForm.append(graphicsGroup);
	

		soundGroup = new ChoiceGroup(literal[19],
				ChoiceGroup.MULTIPLE, new String[]{literal[20]},
				null);
		soundGroup.setSelectedIndex(0, enableSound);
		settingsForm.append(soundGroup);

		languageGroup = new ChoiceGroup(literal[22], ChoiceGroup.EXCLUSIVE,
				languages, null);
		for (int i = 0; i < languages.length; i++)
			languageGroup.setSelectedIndex(i, language == languageLookup[i]);
		settingsForm.append(languageGroup);
		
		settingsForm.addCommand(new Command(literal[10], Command.BACK, 0));
		settingsForm.addCommand(new Command(literal[27], Command.OK, 1));
		settingsForm.setCommandListener(this);
		display.setCurrent(settingsForm);
	}

	private void settingsCommand(Command com) {
		if (com.getCommandType() == Command.BACK) {
			settingsForm = null;
			showMainMenu();
			return;
		}
		
		lazyLoadingThreshold = Math.max(Integer.parseInt(loadThresholdField.getString()) / 16, 20);

		enableSound = soundGroup.isSelected(0);
		showFps = graphicsGroup.isSelected(0);
		int oldLanguage = language;
		language = languageLookup[languageGroup.getSelectedIndex()];
		
		GBCanvas.writeSettings();
		if (oldLanguage != language) {
			readLiteralsFile();
			cartList = null;
		}
		settingsForm = null;
		showMainMenu();
	}
	
	public void addSavegamesToList(List list, Vector cartIDs, Vector filenames) {
		for (int i = 0; i < numCarts; i++) {
			try {
				RecordStore rs = RecordStore.openRecordStore("20R_" + cartID[i], true);

				if (rs.getNumRecords() > 0) {
					list.append(cartDisplayName[i], null);
					cartIDs.addElement(cartID[i]);
					filenames.addElement(cartFileName[i]);
				}
				rs.closeRecordStore();
			} catch (Exception e) {
				if (MeBoy.debug) {
					e.printStackTrace();
				}
				MeBoy.log(e.toString());
			}
		}
	}
	
	
	public void commandAction(Command com, Displayable s) {
		if (s == messageForm)
			messageCommand();
		else if (s == mainMenu)
			mainMenuCommand(com);
		else if (s == cartList)
			cartListCommand(com);
		else if (s == suspendList)
			resumeGameCommand(com);
		else if (s == settingsForm)
			settingsCommand(com);
	}

	public static void log(String s) {
		if (s == null) {
			return;
		}
		logString = logString + s + '\n';
		if (debug) {
			System.out.println(s);
		}
	}
	
	public static void addGameRAM(String name, byte[] data) {
		try {
			RecordStore rs = RecordStore.openRecordStore("20R_" + name, true);
			
			if (rs.getNumRecords() == 0) {
				rs.addRecord(data, 0, data.length);
			} else {
				rs.setRecord(1, data, 0, data.length);
			}
			
			rs.closeRecordStore();
		} catch (Exception e) {
			if (MeBoy.debug)
				e.printStackTrace();
		}
	}

	// name should include number prefix
	public static void addSuspendedGame(String name) {
		String[] oldIndex = suspendName20;
		suspendName20 = new String[oldIndex.length + 1];
		System.arraycopy(oldIndex, 0, suspendName20, 0, oldIndex.length);
		suspendName20[oldIndex.length] = name;
		GBCanvas.writeSettings();
	}
	
	private int findMatch(String match, String[] list, boolean fuzzy) {
		// exact match
		for (int i = 0; i < list.length; i++)
			if (list[i].equals(match))
				return i;
		if (!fuzzy)
			return -1;
		// prefix match, sans extension
		int lastdot = match.lastIndexOf('.');
		if (lastdot != -1 && lastdot > match.length() - 5)
			match = match.substring(0, lastdot);
		for (int i = 0; i < list.length; i++)
			if (list[i].startsWith(match))
				return i;
		return -1;
	}

	static Image makeRotatedImage(String filename){
		Image img = makeImage(filename);
		
		//simple rotation from forum.nokia 
		//http://developer.nokia.com/Community/Wiki/Rotate_an_image_in_Java_ME
		int width = img.getWidth();
		int height = img.getHeight();
	 
		int angle = 90;
		int[] rowData = new int[width];
		int[] rotatedData = new int[width * height];
		
		int rotatedIndex = 0;
		 
		for(int i = 0; i < height; i++)
		{
			img.getRGB(rowData, 0, width, 0, i, width, 1);
	 
			for(int j = 0; j < width; j++)
			{
				rotatedIndex = 
					angle == 90 ? (height - i - 1) + j * height : 
					(angle == 270 ? i + height * (width - j - 1) : 
						width * height - (i * width + j) - 1
					);
	 
				rotatedData[rotatedIndex] = rowData[j];
			}
		}
		return Image.createRGBImage(rotatedData, height, width, true);
		
	}
    // loads a given image by name
    static Image makeImage(String filename) {
        Image image = null;

        try {
            image = Image.createImage(filename);
        } catch (Exception e) {
            // use a null image instead
        }

        return image;
    }
    
	//File selector related functions
	public void fileSelectorExit(){
		showMainMenu();
	}
	
	public void showErrorMsg(String msg){
		log(msg);
	}
	
	public void showError(Exception e){
		showErrorMsg(e.getMessage());
	}
	
	public void loadSelectedRom(String uri){
		int index = uri.lastIndexOf('/');
		String selectedCartDisplayName = uri.substring(index+1, uri.length());//cartDisplayName[ix];
		romName = selectedCartDisplayName;
		String cartDisplayName =selectedCartDisplayName.replace(' ', '_'); //replace whitespaces
		try {
			gbCanvas = new GBCanvas(uri, this, cartDisplayName);
			cartList = null;
			display.setCurrent(gbCanvas);
		} catch (Exception e) {
			showError(null, "error#1", e);
		}
	}
	
	//Stuff returning from the emulator
	public void pauseGame(){
		gbCanvas.pause();
		showMainMenu();
	}
	
	public void resumeGame(){
		display.setCurrent(gbCanvas);
		gbCanvas.resumeGame();
	}


	//interface for the AdScreen
	public void adExit() {
		showMainMenu();
	}
	
	private void startShare(){
	   	registry = Registry.getRegistry(this.getClass().getName());
    	Invocation invocation = new Invocation(null, "text/plain", "com.nokia.share");
    	invocation.setAction("share");
        String[] args = new String[1]; // Only the first element is required and used
        args[0] = new String("text="+"Playing "+ romName + "on " + MeBoySettings.getVersionString());
    	invocation.setArgs(args);
    	invocation.setResponseRequired(false);
    	try {
			registry.invoke(invocation);
		} catch (Exception e) {
		}
	}
	
	public void showNoFileAccessMessage(){
		showMessage("No File Access", "Loading ROMS requires file system access.\n" +
				"Unfortunately you did not give the file access for this app, so the ROM could not be loaded \n");
	}
}
