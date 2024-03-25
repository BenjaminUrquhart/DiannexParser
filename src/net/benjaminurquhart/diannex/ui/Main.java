package net.benjaminurquhart.diannex.ui;

import javax.swing.JFrame;

public class Main {
	
	public static JFrame frame;

	public static void main(String[] args) throws Exception {		
		frame = new JFrame("Diannex.class");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setLocationByPlatform(true);
		frame.add(UI.getInstance());
		
		frame.pack();
		frame.setResizable(false);
		
		frame.requestFocus();
		frame.setVisible(true);
		
		while(true) Thread.sleep(10);
	}

}
