package eme.control.gui;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Timer;
import java.util.TimerTask;

public class LongPressDetector extends MouseAdapter {
    private int id;
    private LongPressDetectedCallback cb;
    private Timer t;
    private int timeout, repeatInterval;

    public LongPressDetector(int id, LongPressDetectedCallback cb) {
        this(id, 1000, cb);
    }

    public LongPressDetector(int id, int timeout, LongPressDetectedCallback cb) {
        this(id, timeout, 200, cb);
    }

    public LongPressDetector(int id, int timeout, int repeatInterval, LongPressDetectedCallback cb) {
        this.id = id;
        this.cb = cb;
        this.timeout = timeout;
        this.repeatInterval = repeatInterval;
    }

    @Override
    public void mousePressed(MouseEvent e) {
        if (t == null) {
            t = new Timer();
        }

        t.schedule(new TimerTask() {
            public void run() {
                cb.onLongPress(id);
            }
        }, timeout, repeatInterval);
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (t != null) {
            t.cancel();
            t = null;
        }
    }
}
