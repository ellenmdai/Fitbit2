package assignment10.fitbit2;

import jssc.SerialPort;
import jssc.SerialPortException;

public class SerialComm {
	
	SerialPort port;
	private boolean debug;
	
	public SerialComm(String name, boolean debugSetting) {
		port = new SerialPort(name);
		debug = debugSetting;
		try {
			port.openPort();
			port.setParams(SerialPort.BAUDRATE_9600, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public SerialComm(String name) {
		this(name, true);
	}
	
	public static void main(String[] args) {
		SerialComm serialComm = new SerialComm("COM3");
		while (true) {
			try {
				while (serialComm.available()) {
					byte b = serialComm.readByte();
					System.out.println((char)b);
//					serialComm.readByte();
				}
			}
			catch (Exception e) {
				e.printStackTrace();
			}
			
		}
	}
	
	public boolean available() throws SerialPortException {
		return (port.getInputBufferBytesCount() > 0);
	}
	
	public byte readByte() throws SerialPortException {
		byte[] byteArray = port.readBytes(1);
		if (debug) {
			System.out.print("[0x" + String.format("%02x", byteArray[0]) + "]");
		}
		return byteArray[0];
	}
	
	public void writeByte(byte theByte) throws SerialPortException {
		port.writeByte(theByte);
		if (debug) {
			System.out.println("<0x" + String.format("%02x", theByte) + ">");
		}
	}
	
	public void setDebug(boolean setting) {
		debug = setting;
	}

}
