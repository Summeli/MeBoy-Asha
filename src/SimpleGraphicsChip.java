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

import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;


public class SimpleGraphicsChip extends GraphicsChip {
	boolean winEnabledThisFrame = true;
	boolean spritesEnabledThisFrame = true;
	private boolean screenFilled;
	private boolean windowStopped;
	private boolean winEnabledThisLine = false;
	private int windowStopLine = 144;
	private int windowStopX;
	private int windowStopY;

	// Hacks to allow some raster effects to work.  Or at least not to break as badly.
	boolean savedWindowDataSelect = false;
	
	// tiles & image cache
	private Image transparentImage;
	private Image[] tileImage;
	private boolean[] tileReadState; // true if there are any images to be invalidated
	private int[][] imageBounds;
	
	private int[] tempPix;

	int tileWidth = 8;
	int tileHeight = 8;
	int imageHeight = 8; // either tileHeight or tileHeight * 2 (when processing doubled sprites).

	private Graphics graphics;
	
	
	public SimpleGraphicsChip(Dmgcpu d) {
		super(d);
		
		colors = new int[] { 0xffffffff, 0xffaaaaaa, 0xff555555, 0xff000000};
		gbcMask = 0xff000000;
		transparentCutoff = cpu.gbcFeatures ? 32 : 0;
		
		tileImage = new Image[tileCount * colorCount];
		imageBounds = new int[tileCount][];
		tileReadState = new boolean[tileCount];
		
		cpu.memory[4] = videoRam;
		
		tempPix = new int[tileWidth * tileHeight * 2];
		transparentImage = Image.createRGBImage(tempPix, tileWidth, tileHeight, true);
		
		frameBufferImage = Image.createImage(scaledWidth, scaledHeight);
		graphics = frameBufferImage.getGraphics();
	}
	
	public void UpdateLCDCFlags(int data) {
		if (doubledSprites != ((data & 0x04) != 0)) {
			invalidateAll(1);
			invalidateAll(2);
		}
		super.UpdateLCDCFlags(data);
		spritesEnabledThisFrame |= spritesEnabled;
	}

	/** Writes data to the specified video RAM address */
	public final void addressWrite(int addr, byte data) {
		if (videoRam[addr] == data)
			return;
		
		if (addr < 0x1800) { // Bkg Tile data area
			int tileIndex = (addr >> 4) + tileOffset;
			
			if (tileReadState[tileIndex]) {
				int r = tileImage.length - tileCount + tileIndex;
				
				do {
					tileImage[r] = null;
					r -= tileCount;
				} while (r >= 0);
				imageBounds[tileIndex] = null;
				tileReadState[tileIndex] = false;
			}
		}
		videoRam[addr] = data;
	}
	
	/** Invalidate all tiles in the tile cache for the given palette */
	public final void invalidateAll(int pal) {
		int start = pal * tileCount * 4;
		int stop = (pal + 1) * tileCount * 4;
		
		for (int r = start; r < stop; r++) {
			tileImage[r] = null;
		}
	}
	
	// called by notifyscanline, and draw
	private final void drawSprites(int priorityFlag) {
		if (!spritesEnabledThisFrame)
			return;
		
		int tileNumMask = 0xff;
		if (doubledSprites) {
			tileNumMask = 0xfe; // last bit treated as 0
			imageHeight = tileHeight * 2;
		}
		
		for (int i = 156; i >= 0; i -= 4) {
			int attributes = 0xff & cpu.oam[i + 3];
			
			if ((attributes & 0x80) == priorityFlag) {
				int spriteX = (0xff & cpu.oam[i + 1]) - 8;
				int spriteY = (0xff & cpu.oam[i]) - 16;
				
				if (spriteX >= 160 || spriteY >= 144 || spriteY == -16)
					continue;
				
				int tileNum = (tileNumMask & cpu.oam[i + 2]);
				int spriteAttrib = (attributes >> 5) & 0x03; // flipx: from bit 0x20 to 0x01, flipy: from bit 0x40 to 0x02

				if (cpu.gbcFeatures) {
					spriteAttrib += 0x20 + ((attributes & 0x07) << 2); // palette
					tileNum += (384>>3) * (attributes & 0x08); // tile vram bank
				} else {
					// attributes 0x10: 0x00 = OBJ1 palette, 0x10 = OBJ2 palette
					// spriteAttrib: 0x04: OBJ1 palette, 0x08: OBJ2 palette
					spriteAttrib += 4 + ((attributes & 0x10) >> 2);
				}
				
				draw(tileNum, spriteX, spriteY, spriteAttrib);
			}
		}
		imageHeight = tileHeight;
	}
	
	/** This must be called by the CPU for each scanline drawn by the display hardware. It
		* handles drawing of the background layer
		*/
	public final void notifyScanline(int line) {
		if (skipping) {
			return;
		}
		
		if (line == 0) {
			if (!cpu.gbcFeatures) {
				graphics.setColor(gbPalette[0]);
				graphics.fillRect(0, 0, scaledWidth, scaledHeight);
				
				// for SimpleGraphics, sprite prio is enabled iff !gbcFeatures
				drawSprites(0x80);
			}
			
			windowStopLine = 144;
			winEnabledThisFrame = winEnabled;
			winEnabledThisLine = winEnabled;
			screenFilled = false;
			windowStopped = false;
		}
		
		if (winEnabledThisLine && !winEnabled) {
			windowStopLine = line & 0xff;
			winEnabledThisLine = false;
		}
		
		// Fix to screwed up status bars. Record which data area is selected on the
		// first line the window is to be displayed. Will work unless this is changed
		// after window is started
		// NOTE: Still no real support for hblank effects on window/sprites
		if (line == (cpu.registers[0x4A] & 0xff) + 1) { // Compare against WY reg
			savedWindowDataSelect = bgWindowDataSelect;
		}
		
		if (!bgEnabled)
			return;
		
		if (winEnabledThisLine && 
				!windowStopped && 
				(cpu.registers[0x4B] & 0xff) - 7 == 0 && // at left edge
				(cpu.registers[0x4A] & 0xff) <= line - 7) { // starts at or above top line of this row of tiles
			
			// the entire row is covered by the window, so it can be safely skipped
			int yPixelOfs = cpu.registers[0x42] & 7;
			int screenY = (line & 0xf8) - yPixelOfs;
			if (screenY >= 136)
				screenFilled = true;
		} else if ((((cpu.registers[0x42] + line) & 7) == 7) || (line == 144)) {
			int xPixelOfs = cpu.registers[0x43] & 7;
			int yPixelOfs = cpu.registers[0x42] & 7;
			int xTileOfs = (cpu.registers[0x43] & 0xff) >> 3;
			int yTileOfs = (cpu.registers[0x42] & 0xff) >> 3;
			
			int bgStartAddress = hiBgTileMapAddress ? 0x1c00 : 0x1800; 
			int tileNum;
			
			int screenY = (line & 0xf8) - yPixelOfs;
			int screenX = -xPixelOfs;
			int screenRight = 160;
			
			int tileY = (line >> 3) + yTileOfs;
			int tileX = xTileOfs;
			
			int memStart = bgStartAddress + ((tileY & 0x1f) << 5);

			while (screenX < screenRight) {
				if (bgWindowDataSelect) {
					tileNum = videoRamBanks[0][memStart + (tileX & 0x1f)] & 0xff;
				} else {
					tileNum = 256 + videoRamBanks[0][memStart + (tileX & 0x1f)];
				}
				
				int tileAttrib = 0;
				if (cpu.gbcFeatures) {
					int mapAttrib = videoRamBanks[1][memStart + (tileX & 0x1f)];
					tileAttrib += (mapAttrib & 0x07) << 2; // palette
					tileAttrib += (mapAttrib >> 5) & 0x03; // mirroring
					tileNum += 384 * ((mapAttrib >> 3) & 0x01); // tile vram bank
					// bit 7 (priority) is ignored
				}
				tileX++;

				draw(tileNum, screenX, screenY, tileAttrib);

				screenX += 8;
			}

			if (screenY >= 136)
				screenFilled = true;
		}
		if (line == 143) {
			if (!screenFilled)
				notifyScanline(144); // fudge to update last part of screen when scrolling in y direction
			updateFrameBufferImage();
		}
	}
	
	private final void updateFrameBufferImage() {
		if (lcdEnabled) {
			/* Draw window */
			if (winEnabledThisFrame) {
				int wx, wy;
				
				int windowStartAddress = hiWinTileMapAddress ? 0x1c00 : 0x1800;

				if (windowStopped) {
					wx = windowStopX;
					wy = windowStopY;
				} else {
					wx = (cpu.registers[0x4B] & 0xff) - 7;
					wy = (cpu.registers[0x4A] & 0xff);
				}

				if (!cpu.gbcFeatures) {
					graphics.setColor(gbPalette[0]);
					int h = windowStopLine - wy;
					int w = 160 - wx;
					graphics.fillRect(((wx * tileWidth) >> 3),
							((wy * tileHeight) >> 3),
							(w * tileWidth) >> 3,
							(h * tileHeight) >> 3);
				}
				
				int tileNum, tileAddress;
				int screenY = wy;
				
				int maxy = 19 - (wy >> 3);
				for (int y = 0; y < maxy; y++) {
					if (wy + y * 8 >= windowStopLine)
						break;
					
					tileAddress = windowStartAddress + (y * 32);
					
					for (int screenX = wx; screenX < 160; tileAddress++) {
						if (savedWindowDataSelect) {
							tileNum = videoRamBanks[0][tileAddress] & 0xff;
						} else {
							tileNum = 256 + videoRamBanks[0][tileAddress];
						}
						
						int tileAttrib = 0;
						if (cpu.gbcFeatures) {
							int mapAttrib = videoRamBanks[1][tileAddress];
							tileAttrib += (mapAttrib & 0x07) << 2; // palette
							tileAttrib += (mapAttrib >> 5) & 0x03; // mirroring
							tileNum += 384 * ((mapAttrib >> 3) & 0x01); // tile vram bank
							// bit 7 (priority) is ignored
						}

						draw(tileNum, screenX, screenY, tileAttrib);
						screenX += 8;
					}
					screenY += 8;
				}
			}
			
			if (cpu.gbcFeatures) {
				// for SimpleGraphics, sprite prio is enabled iff !gbcFeatures
				drawSprites(0x80);
			}
			drawSprites(0);
		} else {
			// lcd disabled
			graphics.setColor(cpu.gbcFeatures ? -1 : gbPalette[0]);
			graphics.fillRect(0, 0, scaledWidth, scaledHeight);
		}
		
		spritesEnabledThisFrame = spritesEnabled;
	}
	
	/** Create the image of a tile in the tile cache by reading the relevant data from video
	 *  memory
	 */
	private final Image updateImage(int tileIndex, int attribs) {
		int index = tileIndex + tileCount * attribs;

		boolean otherBank = (tileIndex >= 384);

		int offset = otherBank ? ((tileIndex - 384) << 4) : (tileIndex << 4);
		
		int paletteStart = attribs & 0xfc;
		
		byte[] vram = otherBank ? videoRamBanks[1] : videoRamBanks[0];
		int[] palette = cpu.gbcFeatures ? gbcPalette : gbPalette;
		boolean transparentPossible = attribs >= transparentCutoff;
		
		int x2c, y2c, x2cstart;
		int croppedWidth, croppedHeight;
		int preshift = 0;
		
		if (!transparentPossible) {
			croppedWidth = tileWidth;
			croppedHeight = imageHeight;
			x2cstart = 4 - tileWidth;
			y2c = 4 - tileHeight;
		} else if (imageBounds[tileIndex] != null) {
			int[] bounds = imageBounds[tileIndex];
			
			croppedWidth = bounds[4];
			croppedHeight = bounds[5];
			y2c = bounds[6];
			x2cstart = bounds[7];
			offset += bounds[8];
			preshift = bounds[9];
		} else {
			int[] bounds = new int[10];

			bounds[0] = tileWidth;
			bounds[1] = imageHeight;

			int preoffset = offset;
			int mask = 0;
			y2c = 4 - tileHeight;
			for (int y = 0; y < imageHeight; y++) {
				int num = vram[preoffset] | vram[preoffset + 1];
				if (num != 0) {
					bounds[1] = Math.min(bounds[1], y);
					bounds[3] = y + 1;
				}

				mask |= num;
				
				y2c += 8;
				while (y2c > 0) {
					y2c -= tileHeight;
					preoffset += 2;
				}
			}
			x2c = 4 - tileWidth;
			for (int x = tileWidth; --x >= 0; ) {
				if ((mask & 1) != 0) {
					bounds[0] = x;
					bounds[2] = Math.max(bounds[2], x + 1);
				}

				x2c += 8;
				while (x2c > 0) {
					x2c -= tileWidth;
					mask >>= 1;
				}
			}
			
			if (bounds[0] == tileWidth) {
				tileImage[index] = transparentImage;
				tileReadState[tileIndex] = true;
				return tileImage[index];
			}

			imageBounds[tileIndex] = bounds;
			
			bounds[2] = tileWidth - bounds[2];
			bounds[3] = imageHeight - bounds[3];
			bounds[4] = croppedWidth = tileWidth - bounds[2] - bounds[0];
			bounds[5] = croppedHeight = imageHeight - bounds[3] - bounds[1];
			
			// precrop top/left
			x2cstart = 4 - tileWidth + (bounds[2] << 3);
			y2c = 4 - tileHeight + (bounds[1] << 3);
			while (y2c > 0) {
				y2c -= tileHeight;
				bounds[8] += 2;
			}
			while (x2cstart > 0) {
				x2cstart -= tileWidth;
				preshift += 2;
			}
			
			bounds[6] = y2c;
			bounds[7] = x2cstart;
			offset += bounds[8];
			bounds[9] = preshift;
		}
		
		// apply flips
		int pixix = 0;
		int pixixdx = 1;
		int pixixdy = 0;
		
		if ((attribs & TILE_FLIPY) != 0) {
			pixixdy = -croppedWidth << 1;
			pixix = croppedWidth * (croppedHeight - 1);
		}
		
		if ((attribs & TILE_FLIPX) == 0) {
			pixixdx = -1;
			pixix += croppedWidth - 1;
			pixixdy += croppedWidth << 1;
		}
		
		int holemask = 0;
		for (int y = croppedHeight; --y >= 0; ) {
			int num = (weaveLookup[vram[offset] & 0xff] + (weaveLookup[vram[offset + 1] & 0xff] << 1)) >> preshift;
			
			x2c = x2cstart;
			for (int x = croppedWidth; --x >= 0; ) {
				tempPix[pixix] = palette[paletteStart + (num & 3)];
				
				pixix += pixixdx;
				
				x2c += 8;
				while (x2c > 0) {
					x2c -= tileWidth;
					num >>= 2;
				}
			}
			pixix += pixixdy;
			
			y2c += 8;
			while (y2c > 0) {
				y2c -= tileHeight;
				holemask |= ~(vram[offset] | vram[offset + 1]);
				offset += 2;
			}
		}
		
		if (holemask >> (preshift >> 1) == 0) {
			transparentPossible = false;
		}
		
		tileImage[index] = Image.createRGBImage(tempPix, croppedWidth, croppedHeight, transparentPossible);
		
		tileReadState[tileIndex] = true;
		
		return tileImage[index];
	}
	
	private final void draw(int tileIndex, int x, int y, int attribs) {
		int ix = tileIndex + tileCount * attribs;
		
		Image im = tileImage[ix];
		
		if (im == null) {
			im = updateImage(tileIndex, attribs);
		}
		
		if (im == transparentImage) {
			return;
		}
		
		if (scale) {
			y = (y * tileHeight) >> 3;
			x = (x * tileWidth) >> 3;
		}
		
		if (attribs >= transparentCutoff) {
			int[] bounds = imageBounds[tileIndex];
			graphics.drawImage(im, x + bounds[(attribs & 1) << 1], y + bounds[1 + (attribs & 2)], 20);
		} else {
			graphics.drawImage(im, x, y, 20);
		}
	}
	
	public final void stopWindowFromLine() {
		windowStopped = true;
		windowStopLine = (cpu.registers[0x44] & 0xff);
		windowStopX = (cpu.registers[0x4B] & 0xff) - 7;
		windowStopY = (cpu.registers[0x4A] & 0xff);
	}

	public void setScale(int screenWidth, int screenHeight) {
		int oldTW = tileWidth;
		int oldTH = tileHeight;
		
		tileWidth = screenWidth / 20;
		tileHeight = screenHeight / 18;
		
		if (MeBoy.keepProportions)
			if (tileWidth < tileHeight)
				tileHeight = tileWidth;
			else
				tileWidth = tileHeight;
		
		scale = tileWidth != 8 || tileHeight != 8;
		
		scaledWidth = tileWidth * 20;
		scaledHeight = tileHeight * 18;
		
		if (tileWidth != oldTW || tileHeight != oldTH) {
			// invalidate cache
			for (int r = 0; r < tileImage.length; r++)
				tileImage[r] = null;
			
			for (int r = 0; r < tileReadState.length; r++) {
				tileReadState[r] = false;
				imageBounds[r] = null;
			}
		}
		
		imageHeight = tileHeight;
		tempPix = new int[tileWidth * tileHeight * 2];
		frameBufferImage = Image.createImage(scaledWidth, scaledHeight);
		graphics = frameBufferImage.getGraphics();
	}
}
