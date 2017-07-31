package assignment10.fitbit2;

import java.awt.Color;
import java.awt.Font;
import java.util.HashMap;
import java.util.LinkedList;

import sedgewick.Draw;

public class Chart extends Draw {
	
	// ys have to be floats
	
	private double xBorder;
	private double yBorder;
	private double xRange;
	private double xIntervalScaled;
	private double yRangeMax;
	private double yRangeMin;
	private Font axesFont = new Font("Axes", Font.PLAIN, 20);
	private Font titleFont = new Font("Title", Font.BOLD, 40);
	private Font tickFont = new Font("Ticks", Font.PLAIN, 20);
	private Font annotationFont = new Font("Annotations", Font.ITALIC, 15);
	private double yTickInterval;
	private double xTickInterval;
	public boolean dynamicY;	// determines if the y axis scale will change if data is greater than range
	
	private String title;
	private String xLabel;
	private String yLabel;
	
	public HashMap<String, LinkedList<Float>> plots;
	public HashMap<String, Color> plotColors;
	public HashMap<String, LinkedList<String>> annotations;
	public double currentFirstX;
	
	public Chart(String name) {
		super(name);
		title = name;
		xBorder = 0;
		yBorder = 0;
		currentFirstX = 0;
		plots = new HashMap<String, LinkedList<Float>>();
		plotColors = new HashMap<String, Color>();
		annotations = new HashMap<String, LinkedList<String>>();
		dynamicY = false;
		enableDoubleBuffering();
	}
	
	public Chart(String name, int canvasWidth, int canvasHeight, String xLabel, String yLabel) {
		this(name);
		this.xLabel = xLabel;
		this.yLabel = yLabel;
		super.setCanvasSize(canvasWidth, canvasHeight);
	}
	
	public void setBorders(double x, double y) {
		xBorder = x;
		yBorder = y;
	}
	
	// The following two methods makes plotting data simpler by setting the end of the border to zero.
	public void setXscale(double max, double interval) {
		// SET BORDERS BEFORE CALLING THIS!
		super.setXscale(-xBorder, max + xBorder);
		xRange = max;
		xTickInterval = interval;
	}
	
	public void setYscale(double min, double max, double interval) {
		// SET BORDERS BEFORE CALLING THIS!
		super.setYscale(min - yBorder, max + yBorder);
		yRangeMax = max;
		yRangeMin = min;
		yTickInterval = interval;
	}
	
	public void setDataPointInterval(int millis) {
		xIntervalScaled = 0.001*millis;
		System.out.println("Interval: " + xIntervalScaled);
	}
	
	public void allowAnnotationsFor(String plotName) {
		if (!annotations.containsKey(plotName)) {
			annotations.put(plotName, new LinkedList<String>());
		}
	}
	
	public void drawAxesAndTitle() {
		setPenColor();
		setPenRadius(0.01);
		line(0, yRangeMin, xRange, yRangeMin);	// x axis
		line(0, yRangeMin, 0, yRangeMax);	// y axis
		setFont(tickFont);
		// tick marks on x axis
		for (double i = xTickInterval; i <= xRange + currentFirstX; i+=xTickInterval) {
			if (i > currentFirstX) {
				line(i - currentFirstX, yRangeMin - yBorder*0.05, i - currentFirstX, yRangeMin + yBorder*0.05);
				text(i - currentFirstX, yRangeMin - yBorder*0.3, Double.toString(i));
			}
		}
		// tick marks on y axis
		double tickStart = yRangeMin + (yTickInterval - (yRangeMin%yTickInterval));
		for (double i = tickStart; i <= yRangeMax; i+=yTickInterval) {
				line(-xBorder*0.05, i, xBorder*0.05, i);
				text(-xBorder*0.3, i, String.format("%.1f", i), 90);
		}
		// labels and title
		setFont(axesFont);
		text(xRange/2, yRangeMin-(yBorder/1.5), xLabel);
		text(-(xBorder/1.5), (yRangeMax+yRangeMin)/2, yLabel, 90);
		setFont(titleFont);
		text(xRange/2, yRangeMax + (yBorder/2), title);
	}
	
	public void addPlot(String name, Color color) {
		plots.put(name, new LinkedList<Float>());
		plotColors.put(name, color);
	}
	
	public void addDataPoint(String plot, float y) {
		boolean annotationsAllowed = annotations.containsKey(plot);
		try {
			plots.get(plot).add(y);
			if (annotationsAllowed)
				annotations.get(plot).add(null);	// essentially shifts the annotations over if the plot uses annotations.
		}
		catch (NullPointerException e) {
			System.out.println("That plot doesn't exist.");
			e.printStackTrace();
		}
		// if data no longer fits in x direction, shift every over to the left
		if ((double)plots.get(plot).size() > (double)xRange/xIntervalScaled) {
			plots.get(plot).remove();
			if (annotationsAllowed)
				annotations.get(plot).remove();
			currentFirstX += xIntervalScaled/plots.size();	// will do this 3 times per time for the steps, works for all
		}
		// adjusting Y scale to fit data if dynamicY is true
		if (dynamicY) {
			if (y < yRangeMin) {	
				yRangeMax = yRangeMax - (yRangeMin-y);
				setYscale(y, yRangeMax, yTickInterval);
				yRangeMin = y;
			}
			else if (y > yRangeMax) {
				yRangeMin = yRangeMin + (y-yRangeMax);
				setYscale(yRangeMin, y, yTickInterval);
				yRangeMax = y;
			}
		}
			
	}
	
	public void plotData() {
		clear();
		drawAxesAndTitle();
		setFont(annotationFont);
		for (String plotName : plots.keySet()) {
			if (plotName.isEmpty())
				continue;
			boolean annotationsAllowed = annotations.containsKey(plotName);	// increased efficiency
			setPenColor(plotColors.get(plotName));
			setPenRadius(.005);
			float prevY = plots.get(plotName).getFirst();
			float currentY = 0;	// maybe this will increase code efficiency, not calling plots.get(plotName).get(i) every single time?
			for (int i = 1; i < plots.get(plotName).size(); i++) {
				currentY = plots.get(plotName).get(i);
				if (prevY >= yRangeMin && prevY <= yRangeMax)
					line((i-1)*xIntervalScaled, prevY, i*xIntervalScaled, currentY);	
				if (annotationsAllowed && annotations.get(plotName).get(i-1) != null) {	// could be more efficient
					setPenColor();
					text((i-1)*xIntervalScaled, prevY + (0.08*(yRangeMax-yRangeMin)), annotations.get(plotName).get(i-1));
					setPenColor(plotColors.get(plotName));
				}
				prevY = currentY;
			}
		}
		show();
	}
	
	public void emptyPlots() {
		for (LinkedList<Float> plot : plots.values()) {
			plot.clear();
		}
		for (LinkedList<String> annots : annotations.values()) {
			annots.clear();
		}
	}
	
	public void annotateLast(String plotName, int offset, String annotation) {
		try {
			int i = annotations.get(plotName).size() - offset - 1;
			annotations.get(plotName).remove(i);
			annotations.get(plotName).add(i, annotation);
		}
		catch (NullPointerException e) {
			System.out.println("That plot doesn't annotate.");
			e.printStackTrace();
		}
	}
	
	
	
	

}
