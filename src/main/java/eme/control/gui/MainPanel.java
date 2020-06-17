package eme.control.gui;

import com.pi4j.io.i2c.I2CBus;
import com.pi4j.io.i2c.I2CFactory;
import eme.control.OsValidator;
import eme.control.dev.input.accel.Mpu6050;
import eme.control.dev.output.servo.MessageSender;
import org.apache.log4j.Logger;

import java.awt.Color;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.Map;

import javax.swing.AbstractButton;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.text.JTextComponent;
import eme.control.gui.actions.Action;

import static com.pi4j.io.i2c.I2CFactory.getInstance;
import static eme.control.dev.input.accel.Mpu6050.MPU6050_I2C_ADDRESS;

public class MainPanel extends WindowAdapter implements ActionListener, LongPressDetectedCallback {
    private static final Logger log = Logger.getLogger(MainPanel.class);

    public enum ACTIONS {COMMAND_ROT_LEFT, COMMAND_ROT_RIGHT, COMMAND_EXIT, COMMAND_TRACK  };
    private Map<ACTIONS, Action> actions;

    private JPanel panel;
    protected JButton b1, b2, b3, b4;
    protected JTextComponent logBox;

    private static MessageSender sender;
    private int position = MessageSender.MID_POSITION;

    MainPanel instance;

    public void log(String message) {
        System.out.println(message);
        logBox.setText(message);
        log.debug(message);
    }

    public void log(Exception ex) {
        logBox.setText(ex.getLocalizedMessage());
        log.warn(ex);
    }

    public void onLongPress(int id) {
        if (id == Direction.CW.ordinal()) {
            buttonActionRotCw();
        } else if (id == Direction.CCW.ordinal()) {
            buttonActionRotCcw();
        }
    }

    public enum Direction {
        CW, CCW
    };

    public MainPanel() throws IOException, I2CFactory.UnsupportedBusNumberException {
        ImageIcon leftButtonIcon = createImageIcon("images/right.gif");
        ImageIcon middleButtonIcon = createImageIcon("images/middle.gif");

        b1 = new JButton("<html><center><b><u>r</u>otate CCW</b><br>" + "<font color=#ffffdd>middle button</font>", leftButtonIcon);
        Font font = b1.getFont().deriveFont(Font.PLAIN);
        b1.setFont(font);
        b1.setVerticalTextPosition(AbstractButton.CENTER);
        b1.setHorizontalTextPosition(AbstractButton.LEADING);
        b1.setMnemonic(KeyEvent.VK_R);
        b1.setActionCommand(ACTIONS.COMMAND_ROT_RIGHT.name());
        b1.addActionListener(this);
        b1.addMouseListener(new LongPressDetector(Direction.CW.ordinal(), this));

        b2 = new JButton("rotat<u>e</u> CW", middleButtonIcon);
        b2.setFont(font);
        b2.setForeground(new Color(0xffffdd));
        b2.setVerticalTextPosition(AbstractButton.BOTTOM);
        b2.setHorizontalTextPosition(AbstractButton.CENTER);
        b2.setMnemonic(KeyEvent.VK_E);
        b2.setActionCommand(ACTIONS.COMMAND_ROT_LEFT.name());
        b2.addActionListener(this);
        b2.addMouseListener(new LongPressDetector(Direction.CCW.ordinal(), this));

        b1.setToolTipText("Click this button to disable the middle button.");
        b2.setToolTipText("This middle button does nothing when you click it.");

        b3 = new JButton("exit");
        b3.setMnemonic(KeyEvent.VK_X);
        b3.setActionCommand(ACTIONS.COMMAND_EXIT.name());
        b3.addActionListener(this);

        b4 = new JButton("track");
        b4.setMnemonic(KeyEvent.VK_T);
        b4.setActionCommand(ACTIONS.COMMAND_TRACK.name());
        b4.addActionListener(this);

        logBox = new JTextField();
        logBox.setText("Log started...");
        // Add Components to this container, using the default FlowLayout.

        panel = new JPanel();
        panel.add(b1);
        panel.add(b2);
        panel.add(b3);
        panel.add(b4);
        panel.add(logBox);
    }

    public void actionPerformed(ActionEvent e) {
        final String command = e.getActionCommand();
        log(command);
        try {
            if (ACTIONS.COMMAND_ROT_RIGHT == ACTIONS.valueOf(command)) {    //TODO make nicer...
                buttonActionRotCw();
            } else if (ACTIONS.COMMAND_ROT_LEFT == ACTIONS.valueOf(command)) {
                buttonActionRotCcw();
            } else if (ACTIONS.COMMAND_TRACK == ACTIONS.valueOf(command)) {
                track();
            } else if (ACTIONS.COMMAND_EXIT == ACTIONS.valueOf(command)) {
                System.exit(0);
            }
        } catch (Exception e2) {
            log(e2);
        }
    }

    @Override
    public void windowOpened(WindowEvent e) {
        position = (MessageSender.MIN_PULSE_DURATION + MessageSender.MAX_PULSE_DURATION) / 2;
        setServoPosition(position);
        log("init. pos=" + position);
    }

    static Mpu6050 chip;
    static {
        try {
            chip = new Mpu6050(getInstance(I2CBus.BUS_1));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void track() throws Exception {
        log("tracking");
        double initialRoll = chip.getRoll(), roll;  //TODO this will be input from Faraday's data
        for(int ct=0; ct<300; ct++) {
            roll = chip.getRoll();
            ROLL_DEV dev = compareRollDeviation(roll, initialRoll);
            log("r=" + (int) roll + ", " + dev);
            if (dev == ROLL_DEV.CW_MOVED) {
                while(compareRollDeviation(roll, initialRoll) != ROLL_DEV.NO_CHANGE) {
                    buttonActionRotCcw(10);
                    Thread.sleep(20);
                    roll = chip.getRoll();
                }
            } else if (dev == ROLL_DEV.CCW_MOVED) {
                while(compareRollDeviation(roll, initialRoll) != ROLL_DEV.NO_CHANGE) {
                    buttonActionRotCw(10);
                    Thread.sleep(20);
                    roll = chip.getRoll();
                }
            } else if (dev == ROLL_DEV.NO_CHANGE) {
                log("on track");
            }
            Thread.sleep(100);
        }
    }

    public enum ROLL_DEV { CCW_MOVED, NO_CHANGE, CW_MOVED };

    int ROLL_TOLERANCE = 2;

    private ROLL_DEV compareRollDeviation(double to, double from) {
        int diff = (int) Math.abs(to - from);
        int sig = Integer.signum((int) (to - from));

        if (diff > ROLL_TOLERANCE) {
            if (sig == 1) {
                return ROLL_DEV.CW_MOVED;
            } else if (sig == -1) {
                return ROLL_DEV.CCW_MOVED;
            }
        }

        return ROLL_DEV.NO_CHANGE;
    }

    private void buttonActionRotCw() {
        buttonActionRotCw(MessageSender.PULSE_STEP_PER_CLICK);
    }

    private void buttonActionRotCw(int step) {
        if (position > MessageSender.MIN_PULSE_DURATION) {
            position = position - step;
        }
        setServoPosition(position);
    }

    private void buttonActionRotCcw() {
        buttonActionRotCcw(MessageSender.PULSE_STEP_PER_CLICK);
    }

    private void buttonActionRotCcw(int step) {
        if (position < MessageSender.MAX_PULSE_DURATION) {
            position = position + step;
        }
        setServoPosition(position);
    }

    private void setServoPosition(int pos) {
        logBox.setBackground(Color.cyan);
        try {
            sender.send(MessageSender.createSetServoMessage(pos));
            log("pos=" + position);
        } catch (Exception e1) {
            log(e1.getLocalizedMessage());
            e1.printStackTrace();
        }
        logBox.setBackground(Color.WHITE);
    }

    /** Returns an ImageIcon, or null if the path was invalid. */
    protected static ImageIcon createImageIcon(String path) {
        java.net.URL imgURL = MainPanel.class.getResource(path);
        if (imgURL != null) {
            return new ImageIcon(imgURL);
        } else {
            System.err.println("Couldn't find file: " + path);
            return null;
        }
    }

    /**
     * Create the GUI and show it. For thread safety, this method should be
     * invoked from the event dispatch thread.
     */
    public void createAndShowGUI() {
        // Create and set up the window.
        JFrame frame = new JFrame("EME controller");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.addWindowListener(this);
        // Add content to the window.
        frame.add(panel);

        // Display the window.
        frame.pack();
        frame.setVisible(true);

        if (OsValidator.isUnix()) {
            System.out.println("production environment, gui in full screen");
            setFullScreen(frame);
        } else {
            System.out.println("development environment, gui in normal application window");
        }
    }

    private static void setFullScreen(JFrame frame) {
        frame.dispose();
        frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        frame.setUndecorated(true);
        frame.setVisible(true);
    }

    public static void main(String[] args) throws UnknownHostException, IOException {
        log.info("smain: chip found=" + chip);
        chip.init(MPU6050_I2C_ADDRESS);

        sender = new MessageSender("localhost", 8888, MessageSender.SERVO_GPIO);
        // Schedule a job for the event dispatch thread:
        // creating and showing this application's GUI.
        javax.swing.SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                try {
                    new MainPanel().createAndShowGUI();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (I2CFactory.UnsupportedBusNumberException e) {
                    e.printStackTrace();
                }
            }
        });
    }
}
