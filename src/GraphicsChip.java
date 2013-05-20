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

import javax.microedition.lcdui.*;


public abstract class GraphicsChip {
	protected final int MS_PER_FRAME = 17;

	/** Tile is flipped horizontally */
	protected final int TILE_FLIPX = 1; // 0x20 in oam attributes
	
	/** Tile is flipped vertically */
	protected final int TILE_FLIPY = 2; // 0x40 in oam attributes

	/** The current contents of the video memory, mapped in at 0x8000 - 0x9FFF */
	protected byte[] videoRam;
	
	protected byte[][] videoRamBanks; // one for gb, two for gbc
	
	/** RGB color values */
	protected int[] colors; // gb color palette
	protected int[] gbPalette = new int[12];
	protected int[] gbcRawPalette = new int[128];
	protected int[] gbcPalette = new int[64];
	protected int gbcMask; // 0xff000000 for simple, 0x80000000 for advanced
	protected int transparentCutoff; // min "attrib" value where transparency can occur
	
	boolean bgEnabled = true;
	boolean winEnabled = true;
	boolean spritesEnabled = true;
	boolean lcdEnabled = true;
	boolean spritePriorityEnabled = true;
	
	// skipping, timing:
	public int timer;
	protected boolean skipping = true; // until graphics is set
	protected int frameCount;
	protected int skipCount;
	
	// some statistics
	int lastSkipCount;
	
	/** Selection of one of two addresses for the BG and Window tile data areas */
	boolean bgWindowDataSelect = true;
	
	/** If true, 8x16 sprites are being used.  Otherwise, 8x8. */
	boolean doubledSprites = false;
	
	/** Selection of one of two address for the BG/win tile map. */
	boolean hiBgTileMapAddress = false;
	boolean hiWinTileMapAddress = false;
	protected int tileOffset; // 384 when in vram bank 1 in gbc mode
	protected int tileCount; // 384 for gb, 384*2 for gbc
	protected int colorCount; // number of "logical" colors = palette indices, 12 for gb, 64 for gbc
	
	protected boolean scale;
	public int scaledWidth = 160;
	public int scaledHeight = 144;
	
	private boolean frameDone = true; // ugly framebuffer "mutex"
	
	protected Dmgcpu cpu;
	
	public Image frameBufferImage;
	
	// lookup table for fast image decoding
	protected static int[] weaveLookup = new int[256];
	static {
		for (int i = 1; i < 256; i++) {
			for (int d = 0; d < 8; d++)
				weaveLookup[i] += ((i >> d) & 1) << (d * 2);
		}
	}
	
	
	/** Create a new GraphicsChip connected to the specified CPU */
	public GraphicsChip(Dmgcpu d) {
		cpu = d;
		
		if (cpu.gbcFeatures) {
			videoRamBanks = new byte[2][0x2000];
			tileCount = 384*2;
			colorCount = 64;
			//     1: image
			// * 384: all images
			// *   2: memory banks
			// *   4: mirrored images
			// *  16: all palettes
		} else {
			videoRamBanks = new byte[1][0x2000];
			tileCount = 384;
			colorCount = 12;
			//     1: image
			// * 384: all images
			// *   4: mirrored images
			// *   3: all palettes
		}

		videoRam = videoRamBanks[0];
		cpu.memory[4] = videoRam;
		
		scale = false;
		
		for (int i = 0; i < gbcRawPalette.length; i++)
			gbcRawPalette[i] = -1000; // non-initialized
		for (int i = 0; i < (gbcPalette.length >> 1); i++)
			gbcPalette[i] = -1; // white
		for (int i = (gbcPalette.length >> 1); i < gbcPalette.length; i++)
			gbcPalette[i] = 0; // transparent
	}
	
	public int unflatten(byte[] flatState, int offset) {
		for (int i = 0; i < videoRamBanks.length; i++) {
			System.arraycopy(flatState, offset, videoRamBanks[i], 0, 0x2000);
			offset += 0x2000;
		}
		
		for (int i = 0; i < 12; i++) {
			if ((i & 3) == 0)
				gbPalette[i] = 0x00ffffff & GBCanvas.getInt(flatState, offset);
			else
				gbPalette[i] = 0xff000000 | GBCanvas.getInt(flatState, offset);
			offset += 4;
		}
		
		UpdateLCDCFlags(cpu.registers[0x40]);
		
		if (cpu.gbcFeatures) {
			setVRamBank(flatState[offset++] & 0xff); // updates tileOffset
			for (int i = 0; i < 128; i++) {
				setGBCPalette(i, flatState[offset++] & 0xff);
			}
		} else {
			invalidateAll(0);
			invalidateAll(1);
			invalidateAll(2);
		}
		
		return offset;
	}
	
	public int flatten(byte[] flatState, int offset) {
		for (int i = 0; i < videoRamBanks.length; i++) {
			System.arraycopy(videoRamBanks[i], 0, flatState, offset, 0x2000);
			offset += 0x2000;
		}
		
		for (int j = 0; j < 12; j++) {
			GBCanvas.setInt(flatState, offset, gbPalette[j]);
			offset += 4;
		}
		
		if (cpu.gbcFeatures) {
			flatState[offset++] = (byte) ((tileOffset != 0) ? 1 : 0);
			for (int i = 0; i < 128; i++) {
				flatState[offset++] = (byte) getGBCPalette(i);
			}
		}
		
		return offset;
	}
	
	public void UpdateLCDCFlags(int data) {
		bgEnabled = true;
				
		// BIT 7
		lcdEnabled = ((data & 0x80) != 0);
				
		// BIT 6
		hiWinTileMapAddress = ((data & 0x40) != 0);

		// BIT 5
		winEnabled = ((data & 0x20) != 0);

		// BIT 4
		bgWindowDataSelect = ((data & 0x10) != 0);

		// BIT 3
		hiBgTileMapAddress = ((data & 0x08) != 0);

		// BIT 2
		doubledSprites = ((data & 0x04) != 0);

		// BIT 1
		spritesEnabled = ((data & 0x02) != 0);

		// BIT 0
		if (cpu.gbcFeatures) {
			spritePriorityEnabled = ((data & 0x01) != 0);
		} else {
			if ((data & 0x01) == 0) {
				// this emulates the gbc-in-gb-mode, not the original gb-mode
				bgEnabled = false;
				winEnabled = false;
			}
		}
	}
	
	public final void vBlank() {
		timer += MS_PER_FRAME;
		
		frameCount++;
		
		if (skipping) {
			skipCount++;
			if (skipCount >= MeBoy.maxFrameSkip) {
				// can't keep up, force draw next frame and reset timer (if lagging)
				skipping = false;
				int lag = (int) System.currentTimeMillis() - timer;
				
				if (lag > MS_PER_FRAME)
					timer += lag - MS_PER_FRAME;
			} else
				skipping = (timer - ((int) System.currentTimeMillis()) < 0);
			return;
		}
		
		lastSkipCount = skipCount;
		frameDone = false;
		cpu.screen.redrawSmall();
		
		int now = (int) System.currentTimeMillis();
		
		if (MeBoy.maxFrameSkip == 0)
			skipping = false;
		else
			skipping = timer - now < 0;
		
		// sleep if too far ahead
		try {
			while (timer > now + MS_PER_FRAME) {
				Thread.sleep(1);
				now = (int) System.currentTimeMillis();
			}
		} catch (InterruptedException e) {
			// e.printStackTrace();
		}
		
		while (!frameDone && !cpu.terminate) {
			Thread.yield();
		}
		skipCount = 0;
	}

	/** Set the palette from the internal Gameboy format */
	public void decodePalette(int startIndex, int data) {
		for (int i = 0; i < 4; i++)
			gbPalette[startIndex + i] = colors[((data >> (2 * i)) & 0x03)];
		gbPalette[startIndex] &= 0x00ffffff; // color 0: transparent
	}
	
	public void setGBCPalette(int index, int data) {
		if (gbcRawPalette[index] == data)
			return;
		
		gbcRawPalette[index] = data;
		if (index >= 0x40 && (index & 0x6) == 0) {
			// stay transparent
			return;
		}
		
		int value = (gbcRawPalette[index | 1] << 8) + gbcRawPalette[index & -2];
		
		gbcPalette[index >> 1] = gbcMask + ((value & 0x001F) << 19) + ((value & 0x03E0) << 6) + ((value & 0x7C00) >> 7);

		invalidateAll(index >> 3);
	}

	public int getGBCPalette(int index) {
		return gbcRawPalette[index];
	}

	public void setVRamBank(int value) {
		tileOffset = value * 384;
		videoRam = videoRamBanks[value];
		cpu.memory[4] = videoRam;
	}
	
	public final void notifyRepainted() {
		frameDone = true;
	}
	
	public void stopWindowFromLine() {}
	
	public abstract void setScale(int screenWidth, int screenHeight);
	
	/** Writes data to the specified video RAM address */
	public abstract void addressWrite(int addr, byte data);
	
	/** Invalidate all tiles in the tile cache for the given palette */
	public abstract void invalidateAll(int pal);

	/** This must be called by the CPU for each scanline drawn by the display hardware.
	 */
	public abstract void notifyScanline(int line);
	
}
