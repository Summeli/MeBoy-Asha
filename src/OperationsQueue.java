/*
 * Copyright @ 2012 Nokia Corporation. All rights reserved. Nokia and Nokia
 * Connecting People are registered trademarks of Nokia Corporation. Oracle and
 * Java are trademarks or registered trademarks of Oracle and/or its affiliates.
 * Other product and company names mentioned herein may be trademarks or trade
 * names of their respective owners. See LICENSE.TXT for license information.
 */

/**
 * Copyright © 2012 Nokia Corporation. All rights reserved.
Nokia and Nokia Connecting People are registered trademarks of Nokia Corporation. 
Oracle and Java are trademarks or registered trademarks of Oracle and/or its
affiliates. Other product and company names mentioned herein may be trademarks
or trade names of their respective owners.

Subject to the conditions below, you may, without charge:

*  Use, copy, modify and/or merge copies of this software and 
   associated content and documentation files (the Software)

*  Publish, distribute, sub-licence and/or sell new software 
   derived from or incorporating the Software.

Some of the documentation, content and/or software maybe licensed under open source
software or other licenses. To the extent such documentation, content and/or software
are included, licenses and/or other terms and conditions shall apply in addition and/or
instead of this notice. The exact terms of the licenses, disclaimers, acknowledgements
and notices are reproduced in the materials provided.

This file, unmodified, shall be included with all copies or substantial portions
of the Software that are distributed in source code form.

The Software cannot constitute the primary value of any new software derived 
from or incorporating the Software.

Any person dealing with the Software shall not misrepresent the source of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, 
INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A 
PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT 
HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION 
OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE 
SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * */
import java.util.*;

// Simple Operations Queue
// It runs in an independent thread and executes Operations serially
class OperationsQueue implements Runnable {

    private volatile boolean running = true;
    private final Vector operations = new Vector();

    OperationsQueue() {
        // Notice that all operations will be done in another
        // thread to avoid deadlocks with GUI thread
        new Thread(this).start();
    }

    void enqueueOperation(Operation nextOperation) {
        operations.addElement(nextOperation);
        synchronized (this) {
            notify();
        }
    }

    // stop the thread
    void abort() {
        running = false;
        synchronized (this) {
            notify();
        }
    }

    public void run() {
        while (running) {
            while (operations.size() > 0) {
                try {
                    // execute the first operation on the queue
                    ((Operation) operations.firstElement()).execute();
                } catch (Exception e) {
                    // Nothing to do. It is expected that each operations handle
                    // their own locally exception but this block is to ensure
                    // that the queue continues to operate
                }
                operations.removeElementAt(0);
            }
            synchronized (this) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    // it doesn't matter
                }
            }
        }
    }
}
