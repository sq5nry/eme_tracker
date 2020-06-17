package eme.control.dev.input.accel;

import com.pi4j.io.i2c.I2CBus;
import com.pi4j.io.i2c.I2CDevice;
import com.pi4j.io.i2c.I2CFactory;

import java.io.IOException;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;

import static eme.control.dev.input.accel.Mpu6050.ACCELERATION_DUPA.G;
import static eme.control.dev.input.accel.Mpu6050.ACCELERATION_DUPA.MS_2;

public class Mpu6050 {
    private static final Logger log = Logger.getLogger(Mpu6050.class);

    // Global Variables
    public static final float EARTH_GRAVITY_ACCELERATION = 9.80665f;
    public static final double RAD = 180 / Math.PI;

    private I2CBus bus;
    private I2CDevice dev;

    public static final int MPU6050_I2C_ADDRESS = 0x68;
    //Scale Modifiers
    public static final float ACCEL_SCALE_MODIFIER_2G = 16384.0f;
    public static final float ACCEL_SCALE_MODIFIER_4G = 8192.0f;
    public static final float ACCEL_SCALE_MODIFIER_8G = 4096.0f;
    public static final float ACCEL_SCALE_MODIFIER_16G = 2048.0f;

    public static final float GYRO_SCALE_MODIFIER_250DEG = 131.0f;
    public static final float GYRO_SCALE_MODIFIER_500DEG = 65.5f;
    public static final float GYRO_SCALE_MODIFIER_1000DEG = 32.8f;
    public static final float GYRO_SCALE_MODIFIER_2000DEG = 16.4f;

    //Pre-defined ranges
    public static final int ACCEL_RANGE_2G = 0x00;
    public static final int ACCEL_RANGE_4G = 0x08;
    public static final int ACCEL_RANGE_8G = 0x10;
    public static final int ACCEL_RANGE_16G = 0x18;

    public static final int GYRO_RANGE_250DEG = 0x00;
    public static final int GYRO_RANGE_500DEG = 0x08;
    public static final int GYRO_RANGE_1000DEG = 0x10;
    public static final int GYRO_RANGE_2000DEG = 0x18;

    //MPU-6050 Registers
    public static final int PWR_MGMT_1 = 0x6B;
    public static final int PWR_MGMT_2 = 0x6C;

    public static final int ACCEL_XOUT0 = 0x3B;
    public static final int ACCEL_YOUT0 = 0x3D;
    public static final int ACCEL_ZOUT0 = 0x3F;

    public static final int TEMP_OUT0 = 0x41;

    public static final int GYRO_XOUT0 = 0x43;
    public static final int GYRO_YOUT0 = 0x45;
    public static final int GYRO_ZOUT0 = 0x47;

    public static final int ACCEL_CONFIG = 0x1C;
    public static final int GYRO_CONFIG = 0x1B;

    public Mpu6050(I2CBus bus) {
        this.bus = bus;
    }

    public void init(int address) throws IOException {
        log.debug("init: getting Mpu6050 device at address=" + address);
        // Wake up the MPU-6050 since it starts in sleep mode
        dev = bus.getDevice(address);
        log.debug("init: device ready=" + dev);

        log.debug("init: setting power mgmt");
        dev.write(PWR_MGMT_1, (byte) 0x00);

        log.debug("init: device initialized");
    }

    // I2C communication methods

    /**
     * Read two i2c registers and combine them.
     * register-- the first register to read from.
     * Returns the combined read results.
     */
    protected int readI2cWord(int register) throws IOException {
        //Read the data from the registers
        int high = dev.read(register);
        int low = dev.read(register + 1);

        int value = (high << 8) + low;

        if (value >= 0x8000) {
            return -((65535 - value) + 1);
        } else {
            return value;
        }
    }

    //MPU-6050 Methods

    /**
     * Reads the temperature from the onboard temperature sensor of the MPU-6050.
     * Returns the temperature in degrees Celcius.
     *
     * @return
     */
    public float getTemperature() throws IOException {
        int rawTemp = readI2cWord(TEMP_OUT0);

        //Get the actual temperature using the formule given in the
        //MPU - 6050 Register Map and Descriptions revision 4.2, page 30
        return (rawTemp / 340.0f) + 36.53f;
    }

    /**
     * Sets the range of the accelerometer to range.
     * <p>
     * accel_range-- the range to set the accelerometer to.Using a pre - defined range is advised.
     * First change it to 0x00 to make sure we write the correct value later
     *
     * @param accel_range
     */
    public void setAccelerationRange(int accel_range) throws IOException {
        dev.write(ACCEL_CONFIG, (byte) 0x00);
        dev.write(ACCEL_CONFIG, (byte) accel_range);
    }

    public int readAccelerationRange() throws IOException {
        return readAccelerationRange(false);
    }

    /**
     * Reads the range the accelerometer is set to.
     * <p>
     * If raw is True, it will return the raw value from the ACCEL_CONFIG register
     * If raw is False, it will return an integer: -1, 2, 4, 8 or 16. When it
     * returns -1 something went wrong.
     *
     * @param raw
     * @return
     */
    public int readAccelerationRange(boolean raw) throws IOException {
        int raw_data = dev.read(ACCEL_CONFIG);

        if (raw) {
            return raw_data;
        } else {
            if (raw_data == ACCEL_RANGE_2G) return 2;
            else if (raw_data == ACCEL_RANGE_4G) return 4;
            else if (raw_data == ACCEL_RANGE_8G) return 8;
            else if (raw_data == ACCEL_RANGE_16G) return 16;
            else return -1;
        }
    }

    public float[] getAcceleration() throws IOException {
        return getAcceleration(MS_2);
    }

    public enum ACCELERATION_DUPA { G, MS_2 };

    /**
     * Gets and returns the X, Y and Z values from the accelerometer.
     *
     * @param unit data in [g] or [m / s ^ 2]
     * @return Returns a dictionary with the measurement results.
     */
    public float[] getAcceleration(ACCELERATION_DUPA unit) throws IOException {
        float x = readI2cWord(ACCEL_XOUT0);
        float y = readI2cWord(ACCEL_YOUT0);
        float z = readI2cWord(ACCEL_ZOUT0);

        if (unit == G) {
            int accel_range = readAccelerationRange(true);
            float accel_scale_modifier = getAccScaleModifier(accel_range);
            x = x / accel_scale_modifier;
            y = y / accel_scale_modifier;
            z = z / accel_scale_modifier;
        } else if (unit == MS_2) {
            x = x * EARTH_GRAVITY_ACCELERATION;
            y = y * EARTH_GRAVITY_ACCELERATION;
            z = z * EARTH_GRAVITY_ACCELERATION;
        }
        return new float[]{x, y, z};
    }

    private static float getAccScaleModifier(int accel_range) {
        float accel_scale_modifier;
        if (accel_range == ACCEL_RANGE_2G) {
            accel_scale_modifier = ACCEL_SCALE_MODIFIER_2G;
        } else if (accel_range == ACCEL_RANGE_4G) {
            accel_scale_modifier = ACCEL_SCALE_MODIFIER_4G;
        } else if (accel_range == ACCEL_RANGE_8G) {
            accel_scale_modifier = ACCEL_SCALE_MODIFIER_8G;
        } else if (accel_range == ACCEL_RANGE_16G) {
            accel_scale_modifier = ACCEL_SCALE_MODIFIER_16G;
        } else {
            System.err.println("Unkown range - accel_scale_modifier set to self.ACCEL_SCALE_MODIFIER_2G");
            accel_scale_modifier = ACCEL_SCALE_MODIFIER_2G;
        }
        return accel_scale_modifier;
    }

    public double getRoll() throws IOException {
        float[] acc = getAcceleration();    //x,y,z
        return Math.atan2(acc[1], acc[2]) * RAD;
    }

    public double getPitch() throws IOException {
        float[] acc = getAcceleration();    //x,y,z
        return Math.atan2(-acc[0], Math.sqrt(acc[1] * acc[1] + acc[2] * acc[2])) * RAD;
    }

    public static void main(String[] args) throws Exception {
        BasicConfigurator.configure();
        log.info("smain: starting demo");

        log.info("smain: looking for Mpu6050 chip on I2C BUS_1 at address=" + MPU6050_I2C_ADDRESS);
        final Mpu6050 chip = new Mpu6050(I2CFactory.getInstance(I2CBus.BUS_1));

        log.info("smain: chip found=" + chip);
        chip.init(MPU6050_I2C_ADDRESS);

        int refreshRate = getRefreshRate(args);
        while (true) {
            System.out.printf("roll=%.2f\t, pitch=%.2f\t, temp=%.2f",
                    chip.getRoll(), chip.getPitch(), chip.getTemperature());
            System.out.println();
            Thread.sleep(refreshRate);
        }
    }

    private static int getRefreshRate(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("expected one argument (mpu6050 read refresh rate in ms)");
        }

        return Integer.parseInt(args[0]);
    }
}
/*
class mpu6050:
    def set_gyro_range(self, gyro_range):
        """Sets the range of the gyroscope to range.

        gyro_range -- the range to set the gyroscope to. Using a pre-defined
        range is advised.
        """
        # First change it to 0x00 to make sure we write the correct value later
        self.bus.write_byte_data(self.address, self.GYRO_CONFIG, 0x00)

        # Write the new range to the ACCEL_CONFIG register
        self.bus.write_byte_data(self.address, self.GYRO_CONFIG, gyro_range)

    def read_gyro_range(self, raw = False):
        """Reads the range the gyroscope is set to.

        If raw is True, it will return the raw value from the GYRO_CONFIG
        register.
        If raw is False, it will return 250, 500, 1000, 2000 or -1. If the
        returned value is equal to -1 something went wrong.
        """
        raw_data = self.bus.read_byte_data(self.address, self.GYRO_CONFIG)

        if raw is True:
            return raw_data
        elif raw is False:
            if raw_data == self.GYRO_RANGE_250DEG:
                return 250
            elif raw_data == self.GYRO_RANGE_500DEG:
                return 500
            elif raw_data == self.GYRO_RANGE_1000DEG:
                return 1000
            elif raw_data == self.GYRO_RANGE_2000DEG:
                return 2000
            else:
                return -1

    def get_gyro_data(self):
        """Gets and returns the X, Y and Z values from the gyroscope.

        Returns the read values in a dictionary.
        """
        x = self.readI2cWord(self.GYRO_XOUT0)
        y = self.readI2cWord(self.GYRO_YOUT0)
        z = self.readI2cWord(self.GYRO_ZOUT0)

        gyro_scale_modifier = None
        gyro_range = self.read_gyro_range(True)

        if gyro_range == self.GYRO_RANGE_250DEG:
            gyro_scale_modifier = self.GYRO_SCALE_MODIFIER_250DEG
        elif gyro_range == self.GYRO_RANGE_500DEG:
            gyro_scale_modifier = self.GYRO_SCALE_MODIFIER_500DEG
        elif gyro_range == self.GYRO_RANGE_1000DEG:
            gyro_scale_modifier = self.GYRO_SCALE_MODIFIER_1000DEG
        elif gyro_range == self.GYRO_RANGE_2000DEG:
            gyro_scale_modifier = self.GYRO_SCALE_MODIFIER_2000DEG
        else:
            print("Unkown range - gyro_scale_modifier set to self.GYRO_SCALE_MODIFIER_250DEG")
            gyro_scale_modifier = self.GYRO_SCALE_MODIFIER_250DEG

        x = x / gyro_scale_modifier
        y = y / gyro_scale_modifier
        z = z / gyro_scale_modifier

        return {'x': x, 'y': y, 'z': z}

    def get_all_data(self):
        """Reads and returns all the available data."""
        temp = self.getTemperature()
        accel = self.getAcceleration()
        gyro = self.get_gyro_data()

        return [accel, gyro, temp]

if __name__ == "__main__":
    mpu = MPU6050(0x68)
    print(mpu.getTemperature())
    accel_data = mpu.getAcceleration()
    print(accel_data['x'])
    print(accel_data['y'])
    print(accel_data['z'])
    gyro_data = mpu.get_gyro_data()
    print(gyro_data['x'])
    print(gyro_data['y'])
    print(gyro_data['z'])

 */