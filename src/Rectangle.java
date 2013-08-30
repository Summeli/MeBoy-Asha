/*
 * Copyright: Antti Pohjola summeli@summeli.fi
 * Very simple Rectangle class, just for checking if coordinate is inside it
 * 
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option)
 * any later version.

 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.

 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 59 Temple
 * Place - Suite 330, Boston, MA 02111-1307, USA.
 * */
public class Rectangle {

    public int left;
    public int right;
    public int top;
    public int bottom;

    public Rectangle()
    {
        this(0,0,0,0);
    }
    public Rectangle(int left, int top, int right, int bottom)
    {
        this.left = left;
        this.top  = top;
        this.right = right;
        this.bottom = bottom;
    }

    public int getHeight()
    {
        return bottom - top;
    }
    public int getWidth()
    {
        return right - left;
    }
    
    public boolean contains(int x, int y){
    	if(x>=left && x<=right && y <= top && y >= bottom)
    		return true;
    	else 
    		return false;
    }

}
