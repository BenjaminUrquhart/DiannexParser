package net.benjaminurquhart.diannex.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Choicer {

	public List<Choice> choices = new ArrayList<>();
	
	public void addChoice(String choice, double percentage, int jump) {
		choices.add(new Choice(choice, percentage, jump));
	}
	
	protected List<Choice> processChoices() {
		List<Choice> choices = new ArrayList<>();
		
		for(Choice choice : this.choices) {
			if(Math.random() < choice.chance) {
				choices.add(choice);
			}
		}
		
		if(choices.isEmpty()) {
			// Reroll in the easiest way possible
			// Hopefully we don't destroy the stack
			return processChoices();
		}
		return choices;
	}
	
	@SuppressWarnings("resource")
	public Choice getChoice() {
		List<Choice> choices = processChoices();
		
		System.out.println("Options: ");
		for(int i = 0; i < choices.size(); i++) {
			System.out.println(i + ": " + choices.get(i));
		}
		Scanner sc = new Scanner(System.in);
		String optStr;
		int option = -1;
		do {
			System.out.print("Pick option #: ");
			optStr = sc.nextLine();
			option = optStr.matches("\\d+") ? Integer.parseInt(optStr) : -1;
		} while(option < 0 || option >= choices.size());
		
		return choices.get(option);
	}
}
