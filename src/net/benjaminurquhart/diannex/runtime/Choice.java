package net.benjaminurquhart.diannex.runtime;

public class Choice {

	public String choice;
	public double chance;
	public int jump;
	
	public Choice(String choice, double chance, int jump) {
		this.choice = choice;
		this.chance = chance;
		this.jump = jump;
	}
	
	public String toString() {
		return choice;
	}
}
