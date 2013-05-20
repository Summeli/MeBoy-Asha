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


public class AdvancedGraphicsChip extends GraphicsChip {
	protected int[] frameBuffer;
	protected int[] scaledBuffer;
	
	// tiles & image cache
	protected int[] transparentImage = new int[0];
	protected int[][] tileImage;
	protected boolean[] tileReadState; // true if there are any images to be invalidated
	
	protected int[] tempPix;
	
	protected int windowSourceLine;

	
	public AdvancedGraphicsChip(Dmgcpu d) {
		super(d);
		
		colors = new int[] { 0x80F8F8F8, 0x80A8A8A8, 0x80505050, 0x80000000};
		gbcMask = 0x80000000;
		transparentCutoff = cpu.gbcFeatures ? 32 : 4;
		
		tileImage = new int[tileCount * colorCount][];
		tileReadState = new boolean[tileCount];
		tempPix = new int[8 * 8];
		frameBuffer = new int[8 * 8 * 20*18];
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

	protected final void drawSpritesForLine(int line) {
		if (!spritesEnabled)
			return;

		int minSpriteY = doubledSprites ? line-15 : line -7;
		
		// either only do priorityFlag == 0 (all foreground),
		// or first 0x80 (background) and then 0 (foreground)
		int priorityFlag = spritePriorityEnabled ? 0x80 : 0;
		
		for (; priorityFlag >= 0; priorityFlag -= 0x80) {
			int oamIx = 159;
			
			while (oamIx >= 0) {
				int attributes = 0xff & cpu.oam[oamIx--];
	
				if ((attributes & 0x80) == priorityFlag || !spritePriorityEnabled) {
					int tileNum = (0xff & cpu.oam[oamIx--]);
					int spriteX = (0xff & cpu.oam[oamIx--]) - 8;
					int spriteY = (0xff & cpu.oam[oamIx--]) - 16;
					
					int offset = line - spriteY;
					if (spriteX >= 160 || spriteY < minSpriteY || offset < 0)
						continue;
					
					if (doubledSprites) {
						tileNum &= 0xFE;
					}
	
					int spriteAttrib = (attributes >> 5) & 0x03; // flipx: from bit 0x20 to 0x01, flipy: from bit 0x40 to 0x02
	
					if (cpu.gbcFeatures) {
						spriteAttrib += 0x20 + ((attributes & 0x07) << 2); // palette
						tileNum += (384>>3) * (attributes & 0x08); // tile vram bank
					} else {
						// attributes 0x10: 0x00 = OBJ1 palette, 0x10 = OBJ2 palette
						// spriteAttrib: 0x04: OBJ1 palette, 0x08: OBJ2 palette
						spriteAttrib += 4 + ((attributes & 0x10) >> 2);
					}
					
					if (priorityFlag == 0x80) {
						// background
						if (doubledSprites) {
							if ((spriteAttrib & TILE_FLIPY) != 0) {
								drawPartBgSprite((tileNum | 1) - (offset >> 3), spriteX, line, offset & 7, spriteAttrib);
							} else {
								drawPartBgSprite((tileNum & -2) + (offset >> 3), spriteX, line, offset & 7, spriteAttrib);
							}
						} else {
							drawPartBgSprite(tileNum, spriteX, line, offset, spriteAttrib);
						}
					} else {
						// foreground
						if (doubledSprites) {
							if ((spriteAttrib & TILE_FLIPY) != 0) {
								drawPartFgSprite((tileNum | 1) - (offset >> 3), spriteX, line, offset & 7, spriteAttrib);
							} else {
								drawPartFgSprite((tileNum & -2) + (offset >> 3), spriteX, line, offset & 7, spriteAttrib);
							}
						} else {
							drawPartFgSprite(tileNum, spriteX, line, offset, spriteAttrib);
						}
					}
				} else {
					oamIx -= 3;
				}
			}
		}
	}

	protected boolean drawBackgroundForLine(int line, int windowLeft, int priority) {
		boolean skippedTile = false;
		
		int sourceY = line + (cpu.registers[0x42] & 0xff);
		int sourceImageLine = sourceY & 7;
		
		int tileNum;
		int tileX = (cpu.registers[0x43] & 0xff) >> 3;
		int memStart = (hiBgTileMapAddress ? 0x1c00 : 0x1800) + ((sourceY & 0xf8) << 2);

		int screenX = -(cpu.registers[0x43] & 7);
		for (; screenX < windowLeft; tileX++, screenX += 8) {
			if (bgWindowDataSelect) {
				tileNum = videoRamBanks[0][memStart + (tileX & 0x1f)] & 0xff;
			} else {
				tileNum = 256 + videoRamBanks[0][memStart + (tileX & 0x1f)];
			}

			int tileAttrib = 0;
			
			if (cpu.gbcFeatures) {
				int mapAttrib = videoRamBanks[1][memStart + (tileX & 0x1f)];
				
				if ((mapAttrib & 0x80) != priority) {
					skippedTile = true;
					continue;
				}
				
				tileAttrib += (mapAttrib & 0x07) << 2; // palette
				tileAttrib += (mapAttrib >> 5) & 0x03; // mirroring
				tileNum += 384 * ((mapAttrib >> 3) & 0x01); // tile vram bank
			}

			drawPartCopy(tileNum, screenX, line, sourceImageLine, tileAttrib);
		}

		if (windowLeft < 160) {
			// window!
			int windowStartAddress = hiWinTileMapAddress ? 0x1c00 : 0x1800;

			int tileAddress;

			int windowSourceTileY = windowSourceLine >> 3;
			int windowSourceTileLine = windowSourceLine & 7;

			tileAddress = windowStartAddress + (windowSourceTileY * 32);

			for (screenX = windowLeft; screenX < 160; tileAddress++, screenX += 8) {
				if (bgWindowDataSelect) {
					tileNum = videoRamBanks[0][tileAddress] & 0xff;
				} else {
					tileNum = 256 + videoRamBanks[0][tileAddress];
				}

				int tileAttrib = 0;
				
				if (cpu.gbcFeatures) {
					int mapAttrib = videoRamBanks[1][tileAddress];
					
					if ((mapAttrib & 0x80) != priority) {
						skippedTile = true;
						continue;
					}
					
					tileAttrib += (mapAttrib & 0x07) << 2; // palette
					tileAttrib += (mapAttrib >> 5) & 0x03; // mirroring
					tileNum += 384 * ((mapAttrib >> 3) & 0x01); // tile vram bank
				}
				
				drawPartCopy(tileNum, screenX, line, windowSourceTileLine, tileAttrib);
			}
		}
		return skippedTile;
	}

	/** This must be called by the CPU for each scanline drawn by the display hardware.
	 */
	public final void notifyScanline(int line) {
		if (skipping || line >= 144) {
			return;
		}

		if (line == 0) {
			windowSourceLine = 0;
		}
		
		// determine the left edge of the window (160 if window is inactive)
		int windowLeft;
		if (winEnabled && (cpu.registers[0x4A] & 0xff) <= line) {
			windowLeft = (cpu.registers[0x4B] & 0xff) - 7;
			if (windowLeft > 160)
				windowLeft = 160;
		} else
			windowLeft = 160;
		
		// step 1: background+window
		boolean skippedAnything = drawBackgroundForLine(line, windowLeft, 0);
		
		// At this point, the high (alpha) byte in the frameBuffer is 0xff for colors 1,2,3 and
		// 0x00 for color 0. Foreground sprites draw on all colors, background sprites draw on
		// top of color 0 only.
		
		// step 2: sprites
		drawSpritesForLine(line);

		// step 3: prio tiles+window
		if (skippedAnything) {
			drawBackgroundForLine(line, windowLeft, 0x80);
		}
		
		if (windowLeft < 160)
			windowSourceLine++;
		
		// step 4: to buffer (only last line)
		if (line == 143) {
			updateFrameBufferImage();
		}
	}

	protected final void updateFrameBufferImage() {
		if (!lcdEnabled) {
			int[] buffer = scale ? scaledBuffer : frameBuffer;
			for (int i = 0; i < buffer.length; i++)
				buffer[i] = -1;
			frameBufferImage = Image.createRGBImage(buffer, scaledWidth, scaledHeight, false);
			return;
		}
		
		if (scale) {
			if (MeBoy.scalingMode == 0) {
				// nearest neighbor horizontal and vertical
				
				int deltaX = (((160 << 10) - 1) / scaledWidth);
				int deltaY = (((144 << 10) - 1) / scaledHeight);
				
				int sy = 0;
				int ry = deltaY >> 1;
				int dst = 0;
				int dstStop = scaledWidth;
				
				for (int y = 0; y < scaledHeight; y++) {
					int rx = deltaX >> 1;
					int src = sy * 160;
					
					while (dst < dstStop) {
						scaledBuffer[dst++] = frameBuffer[src];
						
						rx = (rx & 1023) + deltaX;
						src += rx >> 10;
					}
					
					ry = (ry & 1023) + deltaY;
					sy += ry >> 10;
					dstStop += scaledWidth;
				}
			} else if (MeBoy.scalingMode == 1) {
				// linear horizontal, nearest neighbor vertical
				
				int deltaX = (((159 << 10) - 1) / scaledWidth);
				int deltaY = (((144 << 10) - 1) / scaledHeight);
				
				int sy = 0;
				int ry = deltaY >> 1;
				int dst = 0;
				int dstStop = scaledWidth;
				
				for (int y = 0; y < scaledHeight; y++) {
					int rx = deltaX >> 1;
					int src = sy * 160;
					
					while (dst < dstStop) {
						int rightPart = rx >> 7;
						int leftPart = 8 - rightPart;
						
						scaledBuffer[dst++] = (leftPart * frameBuffer[src] + rightPart * frameBuffer[src+1]) >> 3;
						
						rx += deltaX;
						src += rx >> 10;
						rx &= 1023;
					}
					
					ry = (ry & 1023) + deltaY;
					sy += ry >> 10;
					dstStop += scaledWidth;
				}
			} else if (MeBoy.scalingMode == 2) {
				// nearest neighbor horizontal, linear vertical
				
				int deltaX = (((160 << 10) - 1) / scaledWidth);
				int deltaY = (((143 << 10) - 1) / scaledHeight);
				
				int sy = 0;
				int ry = deltaY >> 1;
				int dst = 0;
				int dstStop = scaledWidth;
				
				for (int y = 0; y < scaledHeight; y++) {
					int rx = deltaX >> 1;
					int src = sy * 160;
					
					while (dst < dstStop) {
						int bottomPart = ry >> 7;
						int topPart = 8 - bottomPart;
						
						scaledBuffer[dst++] = (topPart * frameBuffer[src] + bottomPart * frameBuffer[src+160]) >> 3;
						
						rx += deltaX;
						src += rx >> 10;
						rx &= 1023;
					}
					
					ry += deltaY;
					sy += ry >> 10;
					ry &= 1023;
					dstStop += scaledWidth;
				}
			} else if (MeBoy.scalingMode == 3) {
				// linear horizontal and vertical
				
				int deltaX = (((159 << 10) - 1) / scaledWidth);
				int deltaY = (((143 << 10) - 1) / scaledHeight);
				
				int sy = 0;
				int ry = deltaY >> 1;
				int dst = 0;
				int dstStop = scaledWidth;
				
				for (int y = 0; y < scaledHeight; y++) {
					int bottomPart = ry >> 7; // 0-7
					int topPart = 8 - bottomPart; // 1-8
					
					int rx = deltaX >> 1;
					int src = sy * 160;
					
					while (dst < dstStop) {
						int topRightPart = (rx * topPart) >> 10;
						int topLeftPart = topPart - topRightPart;
						
						int bottomRightPart = (rx * bottomPart) >> 10;
						int bottomLeftPart = bottomPart - bottomRightPart;
						
						scaledBuffer[dst++] = (topLeftPart * frameBuffer[src] + topRightPart * frameBuffer[src+1] +
								bottomLeftPart * frameBuffer[src+160] + bottomRightPart * frameBuffer[src+161]) >> 3;
						
						rx += deltaX;
						src += rx >> 10;
						rx &= 1023;
					}
					
					ry += deltaY;
					sy += ry >> 10;
					ry &= 1023;
					dstStop += scaledWidth;
				}
			}
			
			frameBufferImage = Image.createRGBImage(scaledBuffer, scaledWidth, scaledHeight, false);
		} else {
			frameBufferImage = Image.createRGBImage(frameBuffer, 160, 144, false);
		}
	}

	/** Create the image of a tile in the tile cache by reading the relevant data from video
	 *  memory
	 */
	protected final int[] updateImage(int tileIndex, int attribs) {
		int index = tileIndex + tileCount * attribs;
		
		boolean otherBank = (tileIndex >= 384);
		
		int offset = otherBank ? ((tileIndex - 384) << 4) : (tileIndex << 4);

		int paletteStart = attribs & 0xfc;
		
		byte[] vram = otherBank ? videoRamBanks[1] : videoRamBanks[0];
		int[] palette = cpu.gbcFeatures ? gbcPalette : gbPalette;
		boolean transparent = attribs >= transparentCutoff;
		
		int pixix = 0;
		int pixixdx = 1;
		int pixixdy = 0;
		
		if ((attribs & TILE_FLIPY) != 0) {
			pixixdy = -2*8;
			pixix = 8 * (8 - 1);
		}
		if ((attribs & TILE_FLIPX) == 0) {
			pixixdx = -1;
			pixix += 8 - 1;
			pixixdy += 8 * 2;
		}
		
		for (int y = 8; --y >= 0; ) {
			int num = weaveLookup[vram[offset++] & 0xff] +
					 (weaveLookup[vram[offset++] & 0xff] << 1);
			if (num != 0)
				transparent = false;
			
			for (int x = 8; --x >= 0; ) {
				tempPix[pixix] = palette[paletteStart + (num & 3)];
				pixix += pixixdx;
				
				num >>= 2;
			}
			pixix += pixixdy;
		}
		
		if (transparent) {
			tileImage[index] = transparentImage;
		} else {
			tileImage[index] = tempPix;
			tempPix = new int[8 * 8];
		}
		
		tileReadState[tileIndex] = true;
		
		return tileImage[index];
	}

	// draws one scanline of the block
	// ignores alpha byte, just copies pixels
	protected final void drawPartCopy(int tileIndex, int x, int y, int sourceLine, int attribs) {
		int ix = tileIndex + tileCount * attribs;
		int[] im = tileImage[ix];
		
		if (im == null) {
			im = updateImage(tileIndex, attribs);
		}
		
		int dst = x + y * 160;
		int src = sourceLine * 8;
		int dstEnd = (x + 8 > 160) ? ((y+1) * 160) : (dst + 8);  
		
		if (x < 0) { // adjust left
			dst -= x;
			src -= x;
		}
		
		while (dst < dstEnd)
			frameBuffer[dst++] = im[src++];
	}
	
	// draws one scanline of the block
	// overwrites background when source pixel is opaque
	protected final void drawPartFgSprite(int tileIndex, int x, int y, int sourceLine, int attribs) {
		int ix = tileIndex + tileCount * attribs;
		int[] im = tileImage[ix];
		
		if (im == null) {
			im = updateImage(tileIndex, attribs);
		}
		
		if (im == transparentImage) {
			return;
		}
		
		int dst = x + y * 160;
		int src = sourceLine * 8;
		int dstEnd = (x + 8 > 160) ? ((y+1) * 160) : (dst + 8);  
		
		if (x < 0) { // adjust left
			dst -= x;
			src -= x;
		}
		
		while (dst < dstEnd) {
			if (im[src] < 0) // fast check for 0xff in high byte
				frameBuffer[dst] = im[src];
			
			dst++;
			src++;
		}
	}
	
	// draws one scanline of the block
	// overwrites background when source pixel is opaque and background is transparent
	protected final void drawPartBgSprite(int tileIndex, int x, int y, int sourceLine, int attribs) {
		int ix = tileIndex + tileCount * attribs;
		int[] im = tileImage[ix];
		
		if (im == null) {
			im = updateImage(tileIndex, attribs);
		}
		
		if (im == transparentImage) {
			return;
		}
		
		int dst = x + y * 160;
		int src = sourceLine * 8;
		int dstEnd = (x + 8 > 160) ? ((y+1) * 160) : (dst + 8);  
		
		if (x < 0) { // adjust left
			dst -= x;
			src -= x;
		}
		
		while (dst < dstEnd) {
			if (im[src] < 0 && frameBuffer[dst] >= 0) // fast check for 0xff and 0x00 in high byte
				frameBuffer[dst] = im[src];
			
			dst++;
			src++;
		}
	}
	
	public void setScale(int screenWidth, int screenHeight) {
		if (MeBoy.keepProportions) {
			if (screenWidth * 18 > screenHeight * 20)
				screenWidth = screenHeight * 20 / 18;
			else
				screenHeight = screenWidth * 18 / 20;
		}
		
		if (screenWidth == scaledWidth && screenHeight == scaledHeight)
			return;
		
		scale = screenWidth != 160 || screenHeight != 144;
		
		scaledWidth = screenWidth;
		scaledHeight = screenHeight;
		
		if (scale) {
			scaledBuffer = new int[scaledWidth * scaledHeight];
		} else {
			scaledBuffer = null;
		}
	}
	
	public void setGBCPalette(int index, int data) {
		super.setGBCPalette(index, data);
		
		if ((index & 0x6) == 0) {
			gbcPalette[index >> 1] &= 0x00ffffff;
		}
	}
}
