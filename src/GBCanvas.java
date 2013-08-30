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

import java.util.Hashtable;

import javax.microedition.lcdui.*;
import javax.microedition.rms.*;

import com.nokia.mid.ui.multipointtouch.MultipointTouch;
import com.nokia.mid.ui.multipointtouch.MultipointTouchListener;


public class GBCanvas extends Canvas implements CommandListener, MultipointTouchListener {
	public MeBoy parent;
	private Dmgcpu cpu;
	private int w, h, l, t;
	private int sw, sh, trans; // translation, and screen size (on screen, i.e. scaled and rotated if applicable)
	private int ssw, ssh; // source screen width and height (possibly scaled, not rotated)
	private int clipHeight; // maybe including fps
	private int[] previousTime = new int[16];
	private int previousTimeIx;
	
	Hashtable pointerButtons = new Hashtable();
	private Command pauseCommand = new Command(MeBoy.literal[30], Command.SCREEN, 0);
	private Command resumeCommand = new Command(MeBoy.literal[31], Command.SCREEN, 0);
	private Command saveCommand = new Command(MeBoy.literal[32], Command.SCREEN, 1);
	private Command showFpsCommand = new Command(MeBoy.literal[33], Command.SCREEN, 3);
	private Command fullScreenCommand = new Command(MeBoy.literal[34], Command.SCREEN, 4);
	private Command setButtonsCommand = new Command(MeBoy.literal[35], Command.SCREEN, 5);
	private Command exitCommand;
	
    private final static Image DPAD_IMAGE = MeBoy.makeRotatedImage("/dpad.png");
    private final static Image BUTTONS_IMAGE = MeBoy.makeRotatedImage("/buttons.png");
    private final static Image START_BUTTON_IMAGE = MeBoy.makeRotatedImage("/startbutton.png");
    private final static Image MENU_IMAGE = MeBoy.makeRotatedImage("/menuButton.png");
    
	public final static int BUTTON_LEFT = 1;
	public final static int BUTTON_UP = 2;
	public final static int BUTTON_RIGHT = 0;
	public final static int BUTTON_DOWN = 3;
	public final static int BUTTON_A = 4;
	public final static int BUTTON_B = 5;
	public final static int BUTTON_START = 7;
	public final static int BUTTON_SELECT = 6;
	public final static int BUTTON_MENU = 8;
	public final static int BUTTON_NULL = 10;
	
	private Rectangle UP_BUTTON_RECT;
	private Rectangle DOWN_BUTTON_RECT;
	private Rectangle RIGHT_BUTTON_RECT;
	private Rectangle LEFT_BUTTON_RECT;
	private Rectangle A_BUTTON_RECT;
	private Rectangle B_BUTTON_RECT;
	private Rectangle START_BUTTON_RECT;
	private Rectangle SELECT_BUTTON_RECT;
	private Rectangle MENU_BUTTON_RECT;
	
	private static int[] key = new int[] {KEY_NUM6, KEY_NUM4, KEY_NUM2, KEY_NUM8, KEY_NUM7, KEY_NUM9, KEY_POUND, KEY_STAR};
	private String[] keyName = {
		MeBoy.literal[38],
		MeBoy.literal[39],
		MeBoy.literal[40],
		MeBoy.literal[41],
		MeBoy.literal[42],
		MeBoy.literal[43],
		MeBoy.literal[44],
		MeBoy.literal[45]};
	private int keySetCounter;
	private boolean settingKeys;
	private boolean paused;
	
	private String cartDisplayName;
	private String cartID;
	private String suspendName;
	
    private Thread cpuThread;
	
	
	// Common constructor
	private GBCanvas(MeBoy p, String cartID, String cartDisplayName) {
		parent = p;
		this.cartID = cartID;
		this.cartDisplayName = cartDisplayName;
		
		exitCommand = new Command(MeBoy.literal[36] + " " + cartDisplayName, Command.SCREEN, 6);
		setCommandListener(this);
		
		updateCommands();
		
		setFullScreenMode(MeBoy.fullScreen);
		MultipointTouch multitouch = MultipointTouch.getInstance();
		 
		multitouch.addMultipointTouchListener(this);
	}
	
	// Constructor for loading suspended games
	public GBCanvas(String cartID, MeBoy p, String cartDisplayName,
				String suspendName, byte[] suspendState) {
		this(p, cartID, cartDisplayName);
		
		this.suspendName = suspendName;
		
		cpu = new Dmgcpu(cartID, this, suspendState);
		setDimensions();
		
		cpuThread = new Thread(cpu);
		cpuThread.start();
	}
	
	// Constructor for new games
	public GBCanvas(String cartID, MeBoy p, String cartDisplayName) {
		this(p, cartID, cartDisplayName);
		
		cpu = new Dmgcpu(cartID, this);
		
		if (cpu.hasBattery())
			loadCartRam();
		
		setDimensions();
		
		cpuThread = new Thread(cpu);
		cpuThread.start();
	}
	
	public void pointersChanged(int[] pointerIds) {
		if(pointerIds != null)
		{
			for(int i = 0; i < pointerIds.length; i++)
			{
				int touchState = MultipointTouch.getState(pointerIds[i]);
	 
				int x = MultipointTouch.getX(pointerIds[i]);
				int y = MultipointTouch.getY(pointerIds[i]);
				int button = checkPointerButton(x,y);
				Integer pointer = new Integer(pointerIds[i]);
				if( button == BUTTON_MENU){
					//go menu
					parent.pauseGame();
				}
				if(touchState == MultipointTouch.POINTER_RELEASED && button != BUTTON_NULL){
					pointerButtons.remove(pointer);
					cpu.buttonUp(button);
				}else if(touchState == MultipointTouch.POINTER_PRESSED && button != BUTTON_NULL){
					pointerButtons.put(pointer,new Integer(button));
					cpu.buttonDown(button);
				}else if(touchState == MultipointTouch.POINTER_DRAGGED){	
					//Check if there was a previous button pressed with this pointer
					Integer prevkey = (Integer) pointerButtons.get(pointer);
					//no prevkey, store new one, 
					if( prevkey == null){
						if(button != BUTTON_NULL){
							cpu.buttonDown(button);
							pointerButtons.put(pointer, new Integer(button));
						}
						return;
					}
					
					//check if previouskey was already pressed
					int previousKey = prevkey.intValue();
					if( previousKey != button){
						cpu.buttonUp(previousKey);
						if(button != BUTTON_NULL) {
							cpu.buttonDown(button);
							pointerButtons.remove(pointer);
							pointerButtons.put(pointer, new Integer(button));
						}
					}
				}
			}
		}
		
	}

	public int checkPointerButton( int x, int y){
		if(UP_BUTTON_RECT.contains(x, y)){
			return BUTTON_UP;
		}else if(DOWN_BUTTON_RECT.contains(x, y)){
			return BUTTON_DOWN;
		}else if(RIGHT_BUTTON_RECT.contains(x, y)){
			return BUTTON_RIGHT;
		}else if(LEFT_BUTTON_RECT.contains(x, y)){
			return BUTTON_LEFT;
		}else if(MENU_BUTTON_RECT.contains(x, y)){
			return BUTTON_MENU;
		}else if(A_BUTTON_RECT.contains(x, y)){
			return BUTTON_A;
		}else if(B_BUTTON_RECT.contains(x, y)){
			return BUTTON_B;
		}else if(START_BUTTON_RECT.contains(x, y)){
			return BUTTON_START;
		}else if(SELECT_BUTTON_RECT.contains(x, y)){
			return BUTTON_SELECT;
		}
		return BUTTON_NULL;
	}
	
	
	private void updateCommands() {
		// remove and add all commands, to prevent pause/resume to end up last
		removeCommand(pauseCommand);
		removeCommand(resumeCommand);
		removeCommand(saveCommand);
		removeCommand(showFpsCommand);
		removeCommand(fullScreenCommand);
		removeCommand(setButtonsCommand);
		removeCommand(exitCommand);
		
		if (paused)
			addCommand(resumeCommand);
		else
			addCommand(pauseCommand);
		
		addCommand(saveCommand);
		addCommand(showFpsCommand);
		addCommand(fullScreenCommand);
		addCommand(setButtonsCommand);
		addCommand(exitCommand);
	}
	
	public void setDimensions() {
		w = getWidth();
		h = getHeight();
		
		int[] transs = new int[] {0, 5, 3, 6};
		trans = transs[MeBoy.rotations];
		boolean rotate = (MeBoy.rotations & 1) != 0;
		
		if (MeBoy.enableScaling) {
			int deltah = MeBoy.showFps ? -16: 0;
			cpu.setScale(rotate ? (h+deltah) : w, rotate ? w : (h+deltah));
		}
		ssw = sw = cpu.graphicsChip.scaledWidth;
		clipHeight = ssh = sh = cpu.graphicsChip.scaledHeight;
		
		if (rotate) {
			sw = ssh;
			clipHeight = sh = ssw;
		}
		
		l = (w - sw) / 2;
		t = (h - sh) / 2;
		if (MeBoy.showFps) {
			t -= 8;
			clipHeight += 16;
		}
		
		if (l < 0)
			l = 0;
		if (t < 0)
			t = 0;
		
	    //public Rectangle(xmin, ymax, xmax, ymin)
	    //TODO: set different rects for 40 fulltouch and ASHA 1.0 devices
		UP_BUTTON_RECT = new Rectangle(80,80,120,40);
		LEFT_BUTTON_RECT = new Rectangle(40,40,80,0);
		RIGHT_BUTTON_RECT = new Rectangle(40,120,80,80);
		DOWN_BUTTON_RECT = new Rectangle(0,80,40,40);
		MENU_BUTTON_RECT = new Rectangle(0,220,40,180);
		A_BUTTON_RECT = new Rectangle(0,400,50,350);
		B_BUTTON_RECT = new Rectangle(0,330,50,280);
		START_BUTTON_RECT = new Rectangle(200,400,240,360);
		SELECT_BUTTON_RECT = new Rectangle(200,40,240,0);
	}
	
	public void keyReleased(int keyCode) {
		for (int i = 0; i < 8; i++) {
			if (keyCode == key[i]) {
				cpu.buttonUp(i);
			}
		}
	}
	
	public void keyPressed(int keyCode) {
		if (settingKeys) {
			key[keySetCounter++] = keyCode;
			if (keySetCounter == 8) {
				writeSettings();
				settingKeys = false;
			}
			repaint();
			return;
		}
		
		for (int i = 0; i < 8; i++) {
			if (keyCode == key[i]) {
				cpu.buttonDown(i);
			}
		}
	}
	
	public void commandAction(Command c, Displayable s) {
		try {
			String label = c.getLabel();
			if (label.startsWith(MeBoy.literal[36])) {
				if (cpu.hasBattery())
					saveCartRam();
				
				parent.unloadCart();
				Runtime.getRuntime().gc();
			} else if (label == MeBoy.literal[30]) {
				pause();
			} else if (label == MeBoy.literal[31] && !settingKeys) {
				paused = false;
				updateCommands();
				
				cpuThread = new Thread(cpu);
				cpuThread.start();
			} else if (label == MeBoy.literal[33]) {
				MeBoy.showFps = !MeBoy.showFps;
				setDimensions();
			} else if (label == MeBoy.literal[35] && !settingKeys) {
				pause();
				settingKeys = true;
				keySetCounter = 0;
			} else if (label == MeBoy.literal[32] && !settingKeys) {
				if (!cpu.isTerminated()) {
					cpu.terminate();
					while(cpuThread.isAlive()) {
						Thread.yield();
					}
					suspend();
					
					cpuThread = new Thread(cpu);
					cpuThread.start();
				} else {
					suspend();
				}
			} else if (label == MeBoy.literal[34] && !settingKeys) {
				MeBoy.fullScreen = !MeBoy.fullScreen;
				setFullScreenMode(MeBoy.fullScreen);
				
				setDimensions();
			}
		} catch (Throwable th) {
			MeBoy.log(th.toString());
			MeBoy.showLog();
            if (MeBoy.debug)
                th.printStackTrace();
		}
		repaint();
	}

	public final void pause() {
		if (cpuThread == null)
			return;
		
		//save also in here				
		if (cpu.hasBattery())
			saveCartRam();
		
		paused = true;
		updateCommands();
		
		cpu.terminate();
		while(cpuThread.isAlive()) {
			Thread.yield();
		}
		cpuThread = null;
	}
	
	public final void redrawSmall() {
		repaint(l, t, sw, clipHeight);
	}
	
	public final void paintFps(Graphics g) {
		g.setClip(l, t+sh, sw, 16);
		g.setColor(0x999999);
		g.fillRect(l, t+sh, sw, 16);
		g.setColor(0);
		
		int now = (int) System.currentTimeMillis();
		// calculate moving-average fps
		// 17 ms * 60 fps * 2*16 seconds = 32640 ms
		int estfps = ((32640 + now - previousTime[previousTimeIx]) / (now - previousTime[previousTimeIx])) >> 1;
		previousTime[previousTimeIx] = now;
		previousTimeIx = (previousTimeIx + 1) & 0x0F;
		
		g.drawString(estfps + " fps * " + (cpu.getLastSkipCount() + 1), l+1, t+sh, 20);
	}
	
	public final void paint(Graphics g) {
		if (cpu == null)
			return;
		
		if (settingKeys) {
			g.setColor(0x446688);
			g.fillRect(0, 0, w, h);
			g.setColor(-1);
			int cut = MeBoy.literal[37].indexOf(' ', MeBoy.literal[37].length() * 2/5);
			if (cut == -1)
				cut = 0;
			g.drawString(MeBoy.literal[37].substring(0, cut), w/2, h/2-18, 65);
			g.drawString(MeBoy.literal[37].substring(cut + 1), w/2, h/2, 65);
			g.drawString(keyName[keySetCounter], w/2, h/2+18, 65);
			return;
		}
		
		if (g.getClipWidth() != sw || g.getClipHeight() != clipHeight) {
			g.setColor(0x111111);
			g.fillRect(0, 0, w, h);
		}
		
		if (MeBoy.showFps) {
			paintFps(g);
		}
		
		
		g.setClip(l, t, sw, sh);
		if (cpu.graphicsChip.frameBufferImage == null) {
			g.setColor(0xaaaaaa);
			g.fillRect(l, t, sw, sh);
		} else if (trans == 0) {
			g.drawImage(cpu.graphicsChip.frameBufferImage, l, t, 20);
		} else {
			g.drawRegion(cpu.graphicsChip.frameBufferImage, 0, 0, ssw, ssh, trans, l, t, 20);
		}
		cpu.graphicsChip.notifyRepainted();
		
		if (paused) {
			g.setColor(0);
			g.drawString(MeBoy.literal[30], w/2-1, h/2, 65);
			g.drawString(MeBoy.literal[30], w/2, h/2-1, 65);
			g.drawString(MeBoy.literal[30], w/2+1, h/2, 65);
			g.drawString(MeBoy.literal[30], w/2, h/2+1, 65);
			g.setColor(0xffffff);
			g.drawString(MeBoy.literal[30], w/2, h/2, 65);
		}
		
		//Draw controls
		g.setClip(0, 0, w, h);
		g.drawImage(DPAD_IMAGE, 0, 0, Graphics.TOP | Graphics.LEFT);
		g.drawImage(BUTTONS_IMAGE, 0, h - BUTTONS_IMAGE.getHeight(), Graphics.TOP | Graphics.LEFT);
		g.drawImage(START_BUTTON_IMAGE, w -START_BUTTON_IMAGE.getWidth(), h - START_BUTTON_IMAGE.getHeight(), Graphics.TOP | Graphics.LEFT);
		g.drawImage(START_BUTTON_IMAGE,  w -START_BUTTON_IMAGE.getWidth(), 0, Graphics.TOP | Graphics.LEFT);
		g.drawImage(MENU_IMAGE, 0, h/2, Graphics.TOP | Graphics.LEFT);
	}
	
	public static final void setInt(byte[] b, int i, int v) {
		b[i++] = (byte) (v >> 24);
		b[i++] = (byte) (v >> 16);
		b[i++] = (byte) (v >> 8);
		b[i++] = (byte) (v);
	}
	
	public static final int getInt(byte[] b, int i) {
		int r = b[i++] & 0xFF;
		r = (r << 8) + (b[i++] & 0xFF);
		r = (r << 8) + (b[i++] & 0xFF);
		return (r << 8) + (b[i++] & 0xFF);
	}
	
	public static final void writeSettings() {
		try {
			RecordStore rs = RecordStore.openRecordStore("set", true);
			
			int bLength = 55;
			for (int i = 0; i < MeBoy.suspendName10.length; i++)
				bLength += 1 + MeBoy.suspendName10[i].length();
			for (int i = 0; i < MeBoy.suspendName20.length; i++)
				bLength += 1 + MeBoy.suspendName20[i].length() * 2;
			bLength++;
			
			byte[] b = new byte[bLength];
			
			for (int i = 0; i < 8; i++)
				setInt(b, i * 4, key[i]);
			setInt(b, 32, MeBoy.maxFrameSkip);
			setInt(b, 36, MeBoy.rotations);
			setInt(b, 40, MeBoy.lazyLoadingThreshold);
			setInt(b, 44, MeBoy.suspendCounter);
			setInt(b, 48, MeBoy.suspendName10.length);
			
			int index = 52;
			for (int i = 0; i < MeBoy.suspendName10.length; i++) {
				String s = MeBoy.suspendName10[i];
				b[index++] = (byte) (s.length());
				for (int j = 0; j < s.length(); j++)
					b[index++] = (byte) s.charAt(j);
			}
			
			b[index++] = (byte) ((MeBoy.enableScaling ? 1 : 0) + (MeBoy.keepProportions ? 2 : 0) + (MeBoy.fullScreen
					? 4 : 0) + (MeBoy.disableColor ? 8 : 0) + (MeBoy.enableSound ? 32 : 0) + (MeBoy.advancedSound
					? 64 : 0) + (MeBoy.advancedGraphics ? 128 : 0));
			b[index++] = (byte) MeBoy.language;
			b[index++] = (byte) ((MeBoy.showFps ? 1 : 0) + (MeBoy.showLogItem ? 2 : 0));

			b[index++] = (byte) MeBoy.suspendName20.length;
			for (int i = 0; i < MeBoy.suspendName20.length; i++) {
				// Manual UTF-16, since String.getBytes(encoding) is not well-supported:
				char[] chars = MeBoy.suspendName20[i].toCharArray();
				b[index++] = (byte) (chars.length);
				
				for (int j = 0; j < chars.length; j++) {
					b[index++] = (byte) (chars[j] >> 8);
					b[index++] = (byte) (chars[j]);
				}
			}
			
			if (rs.getNumRecords() == 0) {
				rs.addRecord(b, 0, bLength);
			} else {
				rs.setRecord(1, b, 0, bLength);
			}
			rs.closeRecordStore();
		} catch (Exception e) {
			if (MeBoy.debug) {
				e.printStackTrace();
			}
			MeBoy.log(e.toString());
		}
	}
	
	public static final void readSettings() {
		try {
			MeBoy.suspendName10 = new String[0];
			MeBoy.suspendName20 = new String[0];
			
			RecordStore rs = RecordStore.openRecordStore("set", true);
			if (rs.getNumRecords() > 0) {
				byte[] b = rs.getRecord(1);
				
				for (int i = 0; i < 8; i++)
					key[i] = getInt(b, i * 4);
				if (b.length >= 36)
					MeBoy.maxFrameSkip = getInt(b, 32);
				if (b.length >= 40)
					//MeBoy.rotations = getInt(b, 36);
				if (b.length >= 44)
					MeBoy.lazyLoadingThreshold = getInt(b, 40);
				
				int index = 44;
				if (b.length > index) {
					// suspended games index
					MeBoy.suspendCounter = getInt(b, index);
					index += 4;
					MeBoy.suspendName10 = new String[getInt(b, index)];
					index += 4;
					for (int i = 0; i < MeBoy.suspendName10.length; i++) {
						int slen = b[index++] & 0xff;
						
						MeBoy.suspendName10[i] = new String(b, index, slen);
						index += slen;
					}
				}

				if (b.length > index) {
					// settings, part 1
					MeBoy.enableScaling = (b[index] & 1) != 0;
					MeBoy.keepProportions = (b[index] & 2) != 0;
					MeBoy.fullScreen = (b[index] & 4) != 0;
					//MeBoy.disableColor = (b[index] & 8) != 0;
					MeBoy.language = (b[index] & 16) != 0 ? 1 : 0;
					MeBoy.enableSound = (b[index] & 32) != 0;
					MeBoy.advancedSound = (b[index] & 64) != 0;
					MeBoy.advancedGraphics = (b[index] & 128) != 0;
					index++;
				}

				if (b.length > index) {
					MeBoy.language = b[index++];
				}

				if (b.length > index) {
					// settings, part 2
					MeBoy.showFps = (b[index] & 1) != 0;
					MeBoy.showLogItem = (b[index] & 2) != 0;
					index++;
				}

				if (b.length > index) {
					MeBoy.suspendName20 = new String[b[index++]];
					for (int i = 0; i < MeBoy.suspendName20.length; i++) {
						// Manual UTF-16, since new String(..., encoding) is not well-supported:
						int slen = b[index++] & 0xff;
						char[] chars = new char[slen];
						for (int j = 0; j < slen; j++) {
							chars[j] += (b[index++] & 0xff) << 8;
							chars[j] += (b[index++] & 0xff);
						}
						MeBoy.suspendName20[i] = new String(chars);
					}
				}
			} else {
				writeSettings();
			}
			rs.closeRecordStore();
		} catch (Exception e) {
			if (MeBoy.debug)
				e.printStackTrace();
			MeBoy.log(e.toString());
		}
	}
	
	private final void saveCartRam() {
		try {
			RecordStore rs = RecordStore.openRecordStore("20R_" + cartID, true);
			
			byte[][] ram = cpu.getCartRam();
			
			int bankCount = ram.length;
			int bankSize = ram[0].length;
			int size = bankCount * bankSize + 13;
			
			byte[] b = new byte[size];
			
			for (int i = 0; i < bankCount; i++)
				System.arraycopy(ram[i], 0, b, i * bankSize, bankSize);
			
			System.arraycopy(cpu.getRtcReg(), 0, b, bankCount * bankSize, 5);
			long now = System.currentTimeMillis();
			setInt(b, bankCount * bankSize + 5, (int) (now >> 32));
			setInt(b, bankCount * bankSize + 9, (int) now);
			
			if (rs.getNumRecords() == 0) {
				rs.addRecord(b, 0, size);
			} else {
				rs.setRecord(1, b, 0, size);
			}
			
			rs.closeRecordStore();
		} catch (Exception e) {
			if (MeBoy.debug)
				e.printStackTrace();
		}
	}
	
	private final void loadCartRam() {
		try {
			RecordStore rs = RecordStore.openRecordStore("20R_" + cartID, true);
			
			if (rs.getNumRecords() > 0) {
				byte[][] ram = cpu.getCartRam();
				int bankCount = ram.length;
				int bankSize = ram[0].length;
				
				byte[] b = rs.getRecord(1);
				
				for (int i = 0; i < bankCount; i++)
					System.arraycopy(b, i * bankSize, ram[i], 0, bankSize);
				
				if (b.length == bankCount * bankSize + 13) {
					// load real time clock
					System.arraycopy(b, bankCount * bankSize, cpu.getRtcReg(), 0, 5);
					long time = getInt(b, bankCount * bankSize + 5);
					time = (time << 32) + ((long) getInt(b, bankCount * bankSize + 9) & 0xffffffffL);
					time = System.currentTimeMillis() - time;
					cpu.rtcSkip((int) (time / 1000));
				}
			}
			rs.closeRecordStore();
		} catch (Exception e) {
			if (MeBoy.debug)
				e.printStackTrace();
			MeBoy.log(e.toString());
		}
	}
	
	private final void suspend() {
		try {
			boolean insertIndex = false;
			if (suspendName == null) {
				suspendName = (MeBoy.suspendCounter++) + ": " + cartDisplayName;
				insertIndex = true;
			}
			RecordStore rs = RecordStore.openRecordStore("20S_" + suspendName, true);
			
			byte[] b = cpu.flatten();
			
			if (rs.getNumRecords() == 0) {
				rs.addRecord(cartID.getBytes(), 0, cartID.length());
				rs.addRecord(b, 0, b.length);
			} else {
				rs.setRecord(1, cartID.getBytes(), 0, cartID.length());
				rs.setRecord(2, b, 0, b.length);
			}
			
			rs.closeRecordStore();
			if (insertIndex)
				MeBoy.addSuspendedGame(suspendName);
		} catch (Exception e) {
			MeBoy.showError(null, "error#10", e);
		}
	}
	
    public void releaseReferences() {
		// This code helps the garbage collector on some platforms.
		// (contributed by Alberto Simon)
        cpu.terminate();
        while(cpuThread != null && cpuThread.isAlive()) {
            Thread.yield();
        }
        cpu.releaseReferences();
        cpu = null;
        
        cartID = null;
        cartDisplayName = null;
        previousTime = null;
        parent = null;
        System.gc();
    }

    public boolean isPaused(){
    	return paused;
    }
    public void resumeGame(){
		paused = false;
		updateCommands();
		
		cpuThread = new Thread(cpu);
		cpuThread.start();
    }
}

