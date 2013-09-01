/*

MeBoy fileselector

Copyright 2013 Antti Pohjola
http://www.summeli.fi

The fileselector is based Based on Nokia example FT_ImageViewer

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

import javax.microedition.io.*;
import javax.microedition.io.file.*;
import javax.microedition.lcdui.*;


import OperationsQueue;
import Operation;

// Simple file selector class.
// It naviagtes the file system and shows images currently available
class FileSelector
        extends List
        implements CommandListener, FileSystemListener {

    private final OperationsQueue queue = new OperationsQueue();
    private final static String FILE_SEPARATOR =
            (System.getProperty("file.separator") != null) ? System.getProperty("file.separator") : "/";
    private final static String UPPER_DIR = "..";
    private final MeBoy midlet;
    
    private final static Image ROOT_IMAGE = MeBoy.makeImage("/root.png");
    private final static Image FOLDER_IMAGE = MeBoy.makeImage("/folder.png");
    private final static Image FILE_IMAGE = MeBoy.makeImage("/file.png");
    private final Command openCommand =
            new Command("Open", Command.ITEM, 1);
    private final Command exitCommand =
            new Command("Exit", Command.EXIT, 1);
    private final static int INIT_OP = 2;
    private final static int OPEN_OP = 3;
    private Vector rootsList = new Vector();
    // Stores the current root, if null we are showing all the roots
    private FileConnection currentRoot = null;
    private Ticker ticker = new Ticker("MeBoy");
    private boolean initialized = false;
    FileSelector(MeBoy midlet) {
        super("MeBoy", List.IMPLICIT);
        setTicker(ticker);
        this.midlet = midlet;
        addCommand(openCommand);
        addCommand(exitCommand);
        setSelectCommand(openCommand);
        setCommandListener(this);
    }

    void initialize() {
    	if(!initialized){
    		queue.enqueueOperation(new FileSelectorOperations(INIT_OP));
    		FileSystemRegistry.addFileSystemListener(FileSelector.this);
    		initialized = true;
    	}
    }

    void stop() {
        queue.abort();
        FileSystemRegistry.removeFileSystemListener(this);
    }


    public void commandAction(Command c, Displayable d) {
		if (c.getCommandType() == Command.BACK) {
			midlet.fileSelectorExit();
		}
        if (c == openCommand) {
            queue.enqueueOperation(new FileSelectorOperations(OPEN_OP));
        } else if (c == exitCommand) {
            midlet.fileSelectorExit();
        }
    }


    // Listen for changes in the roots
    public void rootChanged(int state, String rootName) {
        queue.enqueueOperation(new FileSelectorOperations(INIT_OP));
    }

    private void displayAllRoots() {
        ticker.setString("MeBoy - [Roots]");
        deleteAll();
        Enumeration roots = rootsList.elements();
        while (roots.hasMoreElements()) {
            String root = (String) roots.nextElement();
            root = root.replace('/', FILE_SEPARATOR.charAt(0));
            append(root.substring(1), ROOT_IMAGE);
        }
        currentRoot = null;
    }

    
    private void loadRoots() {
        if (!rootsList.isEmpty()) {
            rootsList.removeAllElements();
        }
        try {
            Enumeration roots = FileSystemRegistry.listRoots();
            while (roots.hasMoreElements()) {
                rootsList.addElement("/" +
                        (String) roots.nextElement());
            }
        } catch (Throwable e) {
            midlet.showErrorMsg(e.getMessage());
        }

    }
   

    private void openSelected() {

        int selectedIndex = getSelectedIndex();
        if (selectedIndex >= 0) {
            String selectedFile = getString(selectedIndex);
            if (selectedFile.endsWith(FILE_SEPARATOR)) {
                try {
                    String tmp = selectedFile.replace(FILE_SEPARATOR.charAt(0), '/');
                    if (currentRoot == null) {
                        currentRoot = (FileConnection) Connector.open(
                                "file:///" + tmp, Connector.READ);
                    } else {
                        currentRoot.setFileConnection(tmp);
                    }
                    displayCurrentRoot();
                } catch (IOException e) {
                    midlet.showError(e);
                } catch (SecurityException e) {
                    midlet.showError(e);
                }
            } else if (selectedFile.equals(UPPER_DIR)) {
                if (rootsList.contains(currentRoot.getPath() + currentRoot.getName())) {
                    displayAllRoots();
                } else {
                    try {
                        currentRoot.setFileConnection(UPPER_DIR);
                        displayCurrentRoot();
                    } catch (IOException e) {
                        midlet.showError(e);
                    }
                }
            } else {
                String url = currentRoot.getURL() + selectedFile;
                midlet.loadSelectedRom(url);
            }
        }
    }

    private void displayCurrentRoot() {
        try {
            ticker.setString("MeBoy - [" + currentRoot.getURL() + "]");
            // open the root
            deleteAll();
            append(UPPER_DIR, FOLDER_IMAGE);
            // list all dirs
            Enumeration listOfDirs = currentRoot.list("*", false);
            while (listOfDirs.hasMoreElements()) {
            	
            	String currentDir = ((String) listOfDirs.nextElement());
            	
                if (currentDir.endsWith("/")) {
                    String tmp = currentDir.replace('/', FILE_SEPARATOR.charAt(0));
                    append(tmp, FOLDER_IMAGE);                    // always display the platform specific seperator to the user
                    
                }
            }
            // list all gb files and dont show hidden files
            Enumeration listOfFiles = currentRoot.list("*.gb", false);
            while (listOfFiles.hasMoreElements()) {
                String currentFile = (String) listOfFiles.nextElement();
                if (currentFile.endsWith(FILE_SEPARATOR)) {
                    append(currentFile, FOLDER_IMAGE);
                } else {
                    append(currentFile, FILE_IMAGE);
                }
            }
            
            listOfFiles = currentRoot.list("*.gbc", false);
            while (listOfFiles.hasMoreElements()) {
                String currentFile = (String) listOfFiles.nextElement();
                if (currentFile.endsWith(FILE_SEPARATOR)) {
                    append(currentFile, FOLDER_IMAGE);
                } else {
                    append(currentFile, FILE_IMAGE);
                }
            }
            //Making the top item visible.
            setSelectedIndex(0, true);
        } catch (IOException e) {
            midlet.showError(e);
        } catch (SecurityException e) {
            midlet.showError(e);
        }
    }

    private class FileSelectorOperations implements Operation {

        private final String parameter;
        private final int operationCode;

        FileSelectorOperations(int operationCode) {
            this.parameter = null;
            this.operationCode = operationCode;
        }

        FileSelectorOperations(String parameter, int operationCode) {
            this.parameter = parameter;
            this.operationCode = operationCode;
        }

        public void execute() {
            switch (operationCode) {
                case INIT_OP:
                    loadRoots();
                    displayAllRoots();
                    break;
                case OPEN_OP:
                    openSelected();
                    break;
            }
        }
    }

}

