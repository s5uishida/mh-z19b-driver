package io.github.s5uishida.iot.device.mhz19b.driver;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jssc.SerialPort;
import jssc.SerialPortException;
import jssc.SerialPortTimeoutException;

/*
 * Refer to https://www.winsen-sensor.com/d/files/infrared-gas-sensor/mh-z19b-co2-ver1_0.pdf
 *
 * @author s5uishida
 *
 */
public class MHZ19BDriver {
	private static final Logger LOG = LoggerFactory.getLogger(MHZ19BDriver.class);

	private static final int DEFAULT_TIMEOUT = 1000; // default read timeout (msec)
	private static final int WAIT_CLOSE_INTERVAL = 100; // msec

	private static final int BAUDRATE = SerialPort.BAUDRATE_9600;
	private static final int DATABITS = SerialPort.DATABITS_8;
	private static final int STOPBITS = SerialPort.STOPBITS_1;
	private static final int PARITY = SerialPort.PARITY_NONE;

	private static final byte[] CMD_GAS_CONCENTRATION = {(byte)0xff, 0x01, (byte)0x86, 0x00, 0x00, 0x00, 0x00, 0x00, 0x79};
	private static final byte[] CMD_CALIBRATE_ZERO_POINT = {(byte)0xff, 0x01, (byte)0x87, 0x00, 0x00, 0x00, 0x00, 0x00, 0x78};
	private static final byte[] CMD_AUTO_CALIBRATION_ON_WITHOUT_CHECKSUM = {(byte)0xff, 0x01, (byte)0x79, (byte)0xa0, 0x00, 0x00, 0x00, 0x00};
	private static final byte[] CMD_AUTO_CALIBRATION_OFF_WITHOUT_CHECKSUM = {(byte)0xff, 0x01, (byte)0x79, 0x00, 0x00, 0x00, 0x00, 0x00};

	private static final int CMD_GAS_CONCENTRATION_RET_LENGTH = 9;

	private static final int CALIBRATE_SPAN_POINT_MIN = 1000;

	private final SerialPort serialPort;
	private int timeout; // read timeout (msec)
	private final String logPrefix;

	private final AtomicInteger useCount = new AtomicInteger(0);

	private static final ConcurrentHashMap<String, MHZ19BDriver> map = new ConcurrentHashMap<String, MHZ19BDriver>();

	synchronized public static MHZ19BDriver getInstance(String portName) {
		return getInstance(portName, DEFAULT_TIMEOUT);
	}

	synchronized public static MHZ19BDriver getInstance(String portName, int timeout) {
		MHZ19BDriver mhz19b = map.get(portName);
		if (mhz19b == null) {
			mhz19b = new MHZ19BDriver(portName, timeout);
			map.put(portName, mhz19b);
		}
		mhz19b.setTimeout(timeout);
		return mhz19b;
	}

	private void setTimeout(int timeout) {
		this.timeout = timeout;
	}

	private MHZ19BDriver(String portName) {
		this(portName, DEFAULT_TIMEOUT);
	}

	private MHZ19BDriver(String portName, int timeout) {
		this.serialPort = new SerialPort(Objects.requireNonNull(portName));
		this.timeout = timeout;
		this.logPrefix = "[" + portName + "] ";
	}

	synchronized public void open() throws IOException, InterruptedException {
		try {
			LOG.debug(logPrefix + "before - useCount:{} timeout:{}", useCount.get(), timeout);
			if (useCount.compareAndSet(0, 1)) {
				while (serialPort.isOpened()) {
					Thread.sleep(WAIT_CLOSE_INTERVAL);
				}
				Thread.sleep(WAIT_CLOSE_INTERVAL);
				LOG.info(logPrefix + "opening serial port...");
				serialPort.openPort();
				LOG.info(logPrefix + "opened serial port.");
				serialPort.setParams(BAUDRATE, DATABITS, STOPBITS, PARITY);
			}
		} catch (SerialPortException e) {
			LOG.warn(logPrefix + "failed to open serial port - caught - {}", e.toString());
			throw new IOException(e);
		} finally {
			LOG.debug(logPrefix + "after - useCount:{} timeout:{}", useCount.get(), timeout);
		}
	}

	synchronized public void close() throws IOException {
		try {
			LOG.debug(logPrefix + "before - useCount:{} timeout:{}", useCount.get(), timeout);
			if (useCount.compareAndSet(1, 0)) {
				if (serialPort.isOpened()) {
					LOG.info(logPrefix + "closing serial port...");
					serialPort.closePort();
					LOG.info(logPrefix + "closed serial port.");
				}
			}
		} catch (SerialPortException e) {
			LOG.warn(logPrefix + "failed to close serial port - caught - {}", e.toString());
			throw new IOException(e);
		} finally {
			LOG.debug(logPrefix + "after - useCount:{} timeout:{}", useCount.get(), timeout);
		}
	}

	public boolean isOpened() {
		return serialPort.isOpened();
	}

	public String getPortName() {
		return serialPort.getPortName();
	}

	private void dump(byte[] data, String tag) {
		if (LOG.isTraceEnabled()) {
			StringBuffer sb = new StringBuffer();
			for (byte data1 : data) {
				sb.append(String.format("%02x ", data1));
			}
			LOG.trace(logPrefix + "{}{}", tag, sb.toString().trim());
		}
	}

	private void flush() throws SerialPortException {
		while (true) {
			byte[] in = serialPort.readBytes();
			if (in == null || in.length == 0) {
				break;
			}
			LOG.info(logPrefix + "MH-Z19B CO2 sensor command: read and discard unnecessary buffer {} bytes in read.", in.length);
			dump(in, "MH-Z19B CO2 sensor command: read and discard unnecessary buffer: ");
		}
	}

	private void write(byte[] out) throws IOException {
		try {
			flush();
			dump(out, "MH-Z19B CO2 sensor command: write: ");
			serialPort.writeBytes(out);
		} catch (SerialPortException e) {
			LOG.warn(logPrefix + "caught - {}", e.toString());
			throw new IOException(e);
		}
	}

	private byte[] read(int size) throws IOException {
		try {
			byte[] in = serialPort.readBytes(size, timeout);
			dump(in, "MH-Z19B CO2 sensor command:  read: ");
			return in;
		} catch (SerialPortException e) {
			LOG.warn(logPrefix + "caught - {}", e.toString());
			throw new IOException(e);
		} catch (SerialPortTimeoutException e) {
			LOG.warn(logPrefix + "caught - {}", e.toString());
			throw new IOException(e);
		}
	}

	private int convertInt(byte[] data) {
		int value = 0;
		for (int i = 0; i < data.length; i++) {
			value = (value << 8) + (data[i] & 0xff);
		}
		return value;
	}

	private byte getCheckSum(byte[] data) {
		int ret = 0;
		for (int i = 1; i <= 7; i++) {
			ret += (int)data[i];
		}
		return (byte)(~(byte)(ret & 0x000000ff) + 1);
	}

	private byte[] getCommandWithCheckSum(byte[] baseCommand) {
		byte[] checkSum = {getCheckSum(baseCommand)};
		byte[] data = new byte[baseCommand.length + 1];
		System.arraycopy(baseCommand, 0, data, 0, baseCommand.length);
		System.arraycopy(checkSum, 0, data, baseCommand.length, 1);
		return data;
	}

	public int getGasConcentration() throws IOException {
		write(CMD_GAS_CONCENTRATION);
		byte[] received = read(CMD_GAS_CONCENTRATION_RET_LENGTH);

		byte[] data = {received[2], received[3]};
		int value = convertInt(data);

		return value;
	}

	public void setCalibrateZeroPoint() throws IOException {
		write(CMD_CALIBRATE_ZERO_POINT);
		LOG.info(logPrefix + "set the calibration zero point to 400 ppm.");
	}

	public void setCalibrateSpanPoint(int point) throws IOException {
		if (point < CALIBRATE_SPAN_POINT_MIN) {
			LOG.info(logPrefix + "since span needs at least {} ppm, set it to {} ppm.", CALIBRATE_SPAN_POINT_MIN, CALIBRATE_SPAN_POINT_MIN);
			point = CALIBRATE_SPAN_POINT_MIN;
		}

		byte high = (byte)((point / 256) & 0x000000ff);
		byte low = (byte)((point % 256) & 0x000000ff);
		byte[] CMD_CALIBRATE_SPAN_POINT = {(byte)0xff, 0x01, (byte)0x88, high, low, 0x00, 0x00, 0x00};

		write(getCommandWithCheckSum(CMD_CALIBRATE_SPAN_POINT));
		LOG.info(logPrefix + "set the calibration span point to {} ppm.", point);
	}

	public void setAutoCalibration(boolean set) throws IOException {
		if (set) {
			write(getCommandWithCheckSum(CMD_AUTO_CALIBRATION_ON_WITHOUT_CHECKSUM));
			LOG.info(logPrefix + "set auto calibration to ON.");
		} else {
			write(getCommandWithCheckSum(CMD_AUTO_CALIBRATION_OFF_WITHOUT_CHECKSUM));
			LOG.info(logPrefix + "set auto calibration to OFF.");
		}
	}

	private void setDetectionRange(int range) throws IOException {
		byte high = (byte)((range / 256) & 0x000000ff);
		byte low = (byte)((range % 256) & 0x000000ff);
		byte[] CMD_DETECTION_RANGE = {(byte)0xff, 0x01, (byte)0x99, high, low, 0x00, 0x00, 0x00};

		write(getCommandWithCheckSum(CMD_DETECTION_RANGE));
		LOG.info(logPrefix + "set the detection range to {} ppm.", range);
	}

	public void setDetectionRange2000() throws IOException {
		setDetectionRange(2000);
	}

	public void setDetectionRange5000() throws IOException {
		setDetectionRange(5000);
	}

	/******************************************************************************************************************
	 * Sample main
	 ******************************************************************************************************************/
	public static void main(String[] args) throws IOException {
		MHZ19BDriver mhz19b = null;
		try {
			mhz19b = MHZ19BDriver.getInstance("/dev/ttyAMA0");
			mhz19b.open();
			mhz19b.setDetectionRange5000();

			while (true) {
				int value = mhz19b.getGasConcentration();
				LOG.info("co2:" + value);

				Thread.sleep(10000);
			}
		} catch (InterruptedException e) {
			LOG.warn("caught - {}", e.toString());
		} catch (IOException e) {
			LOG.warn("caught - {}", e.toString());
		} finally {
			if (mhz19b != null) {
				mhz19b.close();
			}
		}
	}
}
