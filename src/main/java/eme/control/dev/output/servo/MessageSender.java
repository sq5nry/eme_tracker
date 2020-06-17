package eme.control.dev.output.servo;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;

//communication with pigpiod server
public class MessageSender {
    private DataOutputStream dOut;
    private DataInputStream dIn;
    private Socket socket;
    private int gpio;

    public static final int SERVO_GPIO = 17;

    private static final int PI_CMD_SERVO = 8;

    public static final int MIN_PULSE_DURATION = 500;
    public static final int MAX_PULSE_DURATION = 1900;
    public static final int PULSE_STEP_PER_CLICK = (MAX_PULSE_DURATION - MIN_PULSE_DURATION) / 40; //40 steps per full scale
    public static final int MID_POSITION = (MessageSender.MIN_PULSE_DURATION + MessageSender.MAX_PULSE_DURATION) / 2;

    public MessageSender(String host, int port, int gpio) throws UnknownHostException, IOException {
        socket = new Socket(host, port);
        dOut = new DataOutputStream(socket.getOutputStream());
        dIn = new DataInputStream(socket.getInputStream());
        this.gpio = gpio;
    }

    public int send(byte[] message) throws IOException {
        dOut.write(message);
        int length = 4;
        if (length > 0) {
            message = new byte[length];
            dIn.readFully(message, 0, message.length); // read the message
        }
        return (int) (message[3]);
    }

    public static byte[] createSetServoMessage(int pulse) {
        return createMessage(PI_CMD_SERVO, SERVO_GPIO,  pulse);
    }

    private static byte[] createMessage(int command, int gpio, int value) {
        return new byte[] { PI_CMD_SERVO, 0, 0, 0, (byte) gpio, 0, 0, 0, (byte)value, (byte)(value >>> 8), 0, 0, 0, 0, 0, 0};
    }
}
