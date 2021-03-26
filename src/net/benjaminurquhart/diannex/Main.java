package net.benjaminurquhart.diannex;

import java.io.File;

import javax.imageio.ImageIO;

public class Main {

	public static void main(String[] args) throws Exception {
		System.setErr(System.out);
		DNXReader reader = new DNXReader(new File("/home/benjamin/Desktop/ts-underswap-demo-1.00-windows/data/game.dxb"));
		
		DNXScene scene = reader.sceneMap.get("int_other.ruined5_c");
		ImageIO.write(scene.graph(reader), "png", new File("out.png"));
	}
}
