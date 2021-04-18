package net.benjaminurquhart.diannex.runtime;

import java.awt.Color;

public enum ANSI {

	ORANGE(new Color(0xFFA500)),
	
	RED(Color.RED),
	GRAY(Color.GRAY),
	CYAN(Color.CYAN),
	BLUE(Color.BLUE),
	GREEN(Color.GREEN),
	BLACK(Color.BLACK),
	WHITE(Color.WHITE),
	YELLOW(Color.YELLOW),
	LIGHT_GRAY(Color.LIGHT_GRAY);
	
	public static final String RESET = "\u001b[0;1m";
	
	private final Color color;
	
	private ANSI(Color color) {
		this.color = color;
	}
	private static String toTrueColorImpl(Color color) {
		return String.format("\u001b[38;2;%d;%d;%dm", color.getRed(), color.getGreen(), color.getBlue());
	}
	public static String toTrueColor(Color color) {
		if((color.getRGB()&0xffffff) == 0xffffff) {
			return RESET;
		}
		return toTrueColorImpl(color);
	}
	public static String toTrueColorBackground(Color color) {
		return String.format("\u001b[48;2;%d;%d;%dm", color.getRed(), color.getGreen(), color.getBlue());
	}
	public Color getColor() {
		return color;
	}
	@Override
	public String toString() {
		return toTrueColorImpl(color);
	}
	
	public static Object getColorFrom(char c) {
		switch(c) {
		case 'R': return RED;
		case '$': return RESET;
		case 'Y': return YELLOW;
		default: throw new IllegalArgumentException("Unknown color code: " + c);
		}
	}
}
