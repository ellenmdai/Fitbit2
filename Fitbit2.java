package assignment10.fitbit2;

import java.awt.Font;
import java.nio.charset.StandardCharsets;

import jssc.SerialPortException;
import sedgewick.Draw;

public class Fitbit2 {
	
	final private SerialComm port;
	public Chart stepWindow;
	public Chart sleepWindow;
	public Chart tempWindow;
	public Draw summaryWindow;
	private final Font SUM_TITLE = new Font("Title", Font.BOLD, 40);
	private final Font SUM_TEXT = new Font("Text", Font.PLAIN, 20); 
	private int currentStepCount;
	private int currentSleepTime;
	private float currentTemp;
	
	public static final int x_TEXT_AREA = 200;
	public static final double Y_ZERO = 1.5;
	public static final int CANVAS_WIDTH = 750;
	public static final int CANVAS_HEIGHT = 400;
	
	private long currentMillis = 1;
	private long lastPedResetMillis = 0;
	
	enum State {
		waitMagic,
		waitKey,
		debug,
		error,
		timestamp,
		pedometer,
		sleep,
		cx,
		cy,
		cz,
		pedReset,
		sleepReset,
		filTemp	
	};
	
	private State currentState;
	
	public Fitbit2(String portname) {
		port = new SerialComm("COM3", false);
		currentState = State.waitMagic;
		currentStepCount = 0;
		currentSleepTime = 0;
		currentTemp = 0;
		
		stepWindow = new Chart("Steps", CANVAS_WIDTH, CANVAS_HEIGHT, "Time (s)", "Force (g)");
		stepWindow.setBorders(5, 0.8);
		stepWindow.setXscale(45, 5);
		stepWindow.setDataPointInterval(200);
		stepWindow.setYscale(-1,3, 1);
		stepWindow.setPenRadius(0.01);
		stepWindow.drawAxesAndTitle();
		stepWindow.show();
		stepWindow.addPlot("cx", Draw.BLUE);
		stepWindow.addPlot("cy", Draw.GREEN);
		stepWindow.addPlot("cz", Draw.RED);
		stepWindow.allowAnnotationsFor("cz");
		
		sleepWindow = new Chart("Sleep Time", CANVAS_WIDTH, CANVAS_HEIGHT, "Total time (s)", "Good sleep time (s)");
		sleepWindow.setBorders(5, 5);
		sleepWindow.setXscale(45, 5);
		sleepWindow.setDataPointInterval(1000);
		sleepWindow.setYscale(0, 30, 5);
		sleepWindow.dynamicY = true;
		sleepWindow.setPenRadius(0.01);
		sleepWindow.drawAxesAndTitle();
		sleepWindow.show();
		sleepWindow.addPlot("sleep time", Draw.MAGENTA);
		
		tempWindow = new Chart("Temperature", CANVAS_WIDTH, CANVAS_HEIGHT, "Time (s)", "Temperature (C)");
		tempWindow.setBorders(5, 5);
		tempWindow.setXscale(45, 5);
		tempWindow.setDataPointInterval(1000);
		tempWindow.setYscale(10, 30, 5);
		tempWindow.setPenRadius(0.01);
		tempWindow.drawAxesAndTitle();
		tempWindow.show();
		tempWindow.addPlot("temps", Draw.ORANGE);
		
		summaryWindow = new Draw("summary");
		summaryWindow.setCanvasSize(400, 400);
		summaryWindow.setXscale(0, 400);
		summaryWindow.setYscale(0, 400);
		summaryWindow.enableDoubleBuffering();
	}

	public void run() {
		while (true) {
			try {
				while (port.available()) {
					switch (currentState) {
					case waitMagic:
						if (port.readByte() == '!') {
							currentState = State.waitKey;
						}
						break;
					case waitKey:
						switch (port.readByte()) {
						case (0x30) :
							currentState = State.debug;
							break;
						case (0x31) :
							currentState = State.error;
							break;
						case (0x32) :
							currentState = State.timestamp;
							break;
						case (0x36) :
							currentState = State.filTemp;
							break;
						case (0x37) :
							currentState = State.pedometer;
							break;
						case (0x38) :
							currentState = State.sleep;
							break;
						case (0x39) :
							currentState = State.cx;
							break;
						case (0x3a) :
							currentState = State.cy;
							break;
						case (0x3b) :
							currentState = State.cz;
							break;
						case (0x3c)	:
							currentState = State.pedReset;
							break;
						case (0x3d) :
							currentState = State.sleepReset;
							break;
						default :
							System.out.println("!!!! ERROR IN KEY ZOMG !!!");
							currentState = State.waitMagic;
						}
						break;
					case debug:
						System.out.print("Debug message: ");
						int debugLength = 0;
						debugLength += (port.readByte() & 0xff) << 8;
						debugLength += port.readByte() & 0xff;
						byte[] stringD = new byte[debugLength];
						for (int i = 0; i < debugLength; i++) {
							stringD[i] = port.readByte();
						}
						System.out.println(new String(stringD, StandardCharsets.UTF_8));							
						currentState = State.waitMagic;
						break;
					case error:
						System.out.print("Error message: ");
						int errorLength = 0;
						errorLength += (port.readByte() & 0xff) << 8;
						errorLength += port.readByte() & 0xff;
						byte[] stringE = new byte[errorLength];
						for (int i = 0; i < errorLength; i++) {
							stringE[i] = port.readByte();
						}
						System.out.println(new String(stringE, StandardCharsets.UTF_8));
						currentState = State.waitMagic;
						break;
					case timestamp:
						System.out.print("Timestamp: ");
						int time = 0;
						time += ((port.readByte() & 0xff) << 24);
						time += ((port.readByte() & 0xff) << 16);
						time += ((port.readByte() & 0xff) << 8);
						time += (port.readByte() & 0xff);
						currentMillis = time;
						System.out.println(time + " millis");
						currentState = State.waitMagic;
						break;
					case filTemp:
						System.out.print("Filtered temperature value: ");
						int filtered = 0;
						filtered += ((port.readByte() & 0xff) << 24);
						filtered += ((port.readByte() & 0xff) << 16);
						filtered += ((port.readByte() & 0xff) << 8);
						filtered += (port.readByte() & 0xff);
						currentTemp = Float.intBitsToFloat(filtered);
						System.out.println(currentTemp + " C");
						tempWindow.addDataPoint("temps", currentTemp);
						tempWindow.plotData();
						updateSummary();
						currentState = State.waitMagic;
						break;
					case pedometer:
						int steps = 0;
						steps += ((port.readByte() & 0xff) << 8);
						steps += (port.readByte() & 0xff);
						currentStepCount = steps;
						stepWindow.annotateLast("cz", 0, Integer.toString(currentStepCount));
						System.out.println("Steps: " + steps);
						currentState = State.waitMagic;
						break;
					case sleep:
						System.out.print("Time asleep: ");
						int sleepTime = 0;
						sleepTime += ((port.readByte() & 0xff) << 24);
						sleepTime += ((port.readByte() & 0xff) << 16);
						sleepTime += ((port.readByte() & 0xff) << 8);
						sleepTime += (port.readByte() & 0xff);
						sleepWindow.addDataPoint("sleep time", sleepTime);
						sleepWindow.plotData();
						currentSleepTime = sleepTime;
						System.out.println(sleepTime + " seconds");
						currentState = State.waitMagic;
						break;
					case cx:
						int cx = 0;
						cx += ((port.readByte() & 0xff) << 24);
						cx += ((port.readByte() & 0xff) << 16);
						cx += ((port.readByte() & 0xff) << 8);
						cx += (port.readByte() & 0xff);
						System.out.println("X: " + Float.intBitsToFloat(cx));
						stepWindow.addDataPoint("cx", Float.intBitsToFloat(cx));
						currentState = State.waitMagic;
						break;
					case cy:
						int cy = 0;
						cy += ((port.readByte() & 0xff) << 24);
						cy += ((port.readByte() & 0xff) << 16);
						cy += ((port.readByte() & 0xff) << 8);
						cy += (port.readByte() & 0xff);
						System.out.println("Y: " + Float.intBitsToFloat(cy));
						stepWindow.addDataPoint("cy", Float.intBitsToFloat(cy));
						currentState = State.waitMagic;
						break;
					case cz:
						int cz = 0;
						cz += ((port.readByte() & 0xff) << 24);
						cz += ((port.readByte() & 0xff) << 16);
						cz += ((port.readByte() & 0xff) << 8);
						cz += (port.readByte() & 0xff);
						System.out.println("Z: " + Float.intBitsToFloat(cz));
						stepWindow.addDataPoint("cz", Float.intBitsToFloat(cz));
						stepWindow.plotData();
						currentState = State.waitMagic;
						break;
					case pedReset:
						stepWindow.currentFirstX = 0;
						stepWindow.emptyPlots();
						lastPedResetMillis = currentMillis - 1;
						updateSummary();
						currentState = State.waitMagic;
						break;
					case sleepReset:
						sleepWindow.currentFirstX = 0;
						sleepWindow.setYscale(0, 30, 5);
						sleepWindow.emptyPlots();
						updateSummary();
						currentState = State.waitMagic;
						break;
					default:
						System.out.println("!!!! ERROR IN VALUE ZOMG !!!");
						currentState = State.waitMagic;
					}
				}
			}
			catch (Exception e) {
				e.printStackTrace();
			}
			
		}

	}
	
	private void updateSummary() {
		double stepRate = 3600000*currentStepCount / (currentMillis - lastPedResetMillis);
		summaryWindow.clear();
		summaryWindow.setFont(SUM_TITLE);
		summaryWindow.text(200, 350, "Data Summary");
		summaryWindow.setFont(SUM_TEXT);
		summaryWindow.textLeft(50, 275, "Step count: " + currentStepCount + " steps");
		summaryWindow.textLeft(50, 200, "Step rate: " + String.format("%.0f", stepRate) + " steps per hour");
		summaryWindow.textLeft(50,  125, "Sleep time: " + currentSleepTime + " seconds");
		summaryWindow.textLeft(50,  50, "Temperature: " + String.format("%.2f", currentTemp) + " C");
		summaryWindow.show();
	}

	public static void main(String[] args) {
		Fitbit2 fitbit = new Fitbit2("COM3"); // Adjust this to be the right port for your machine
		fitbit.run();
	}
}
