package net.benjaminurquhart.diannex;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.benjaminurquhart.diannex.runtime.ANSI;
import net.benjaminurquhart.diannex.runtime.DNXRuntime;
import net.benjaminurquhart.diannex.runtime.ExternalDNXFunction;
import net.benjaminurquhart.diannex.runtime.ExternalFunction;
import net.benjaminurquhart.diannex.runtime.RuntimeContext;
import net.benjaminurquhart.diannex.runtime.Value;
import net.benjaminurquhart.diannex.runtime.ValueStack;

public class Main {
	
	public static class TSUSFunctions {
		
		private static Map<String, Integer> flags = new HashMap<>(), persistFlags = new HashMap<>();
		
		public static boolean isGeno;
		
		@ExternalDNXFunction
		public static void setFlag(String flag, int val) {
			System.out.printf("%sSet flag: %s = %s%s\n", ANSI.GRAY, flag, val, ANSI.RESET);
			flags.put(flag, val);
		}
		
		@ExternalDNXFunction
		public static int getFlag(String flag) {
			int val = flags.computeIfAbsent(flag, f -> 0);
			System.out.printf("%sGet flag: %s = %s%s\n", ANSI.GRAY, flag, val, ANSI.RESET);
			return val;
		}
		
		@ExternalDNXFunction
		public static void setPersistFlag(String flag, int val) {
			System.out.printf("%sSet persistent flag: %s = %s%s\n", ANSI.GRAY, flag, val, ANSI.RESET);
			persistFlags.put(flag, val);
		}
		
		@ExternalDNXFunction
		public static int getPersistFlag(String flag) {
			int val = persistFlags.computeIfAbsent(flag, f -> 0);
			System.out.printf("%sGet persistent flag: %s = %s%s\n", ANSI.GRAY, flag, val, ANSI.RESET);
			return val;
		}
		
		@ExternalDNXFunction("goto")
		public static Value sceneGoto(RuntimeContext context, String name) {
			DNXScene scene = context.file.sceneByName(name);
			if(scene == null) {
				throw new IllegalArgumentException("No such scene: " + name);
			}
			System.out.printf("%sGoto: %s%s\n", ANSI.GRAY, scene, ANSI.RESET);
			Map<Integer, Value> oldLocalVars = context.localVars;
			ValueStack oldStack = context.stack;
			context.localVars = new HashMap<>();
			context.stack = new ValueStack();
			Value retVal =  context.runtime.eval(scene);
			context.localVars = oldLocalVars;
			context.stack = oldStack;
			return retVal;
		}
		
		@ExternalDNXFunction("_temTriggerBattle")
		public static Value enterTemBattle(RuntimeContext context) {
			return sceneGoto(context, "tem.battle");
		}
		
		@ExternalDNXFunction
		public static boolean isGeno() {
			System.out.printf("%sisGeno: %s%s\n", ANSI.GRAY, isGeno, ANSI.RESET);
			return isGeno;
		}
	}

	
	public static void main(String[] args) throws Exception {
		System.out.print(ANSI.RESET);
		
		Pattern pattern = Pattern.compile("(`([^`]+)`)", Pattern.CASE_INSENSITIVE);
		
		DNXFile file = new DNXFile(new File("tsus-1.00/data/game_orig.dxb"));
		
		DNXRuntime runtime = new DNXRuntime(file);
		RuntimeContext context = runtime.getContext();
		
		//context.makeHeadless();
		context.setVerbose(true);
		context.autodefineGlobals(true);
		context.setGlobal("global.playerkills", 21);
		context.setGlobal("global.areapopulations", new Integer[] { 0, 0, 0, 0 });
		context.registerExternalFunctions(ExternalFunction.getFrom(TSUSFunctions.class));
		
		context.setMissingExternalFunctionHandler((name, arguments) -> {
			System.out.printf("%sFunction stub: %s(args=%s)%s\n", ANSI.GRAY, name, Arrays.deepToString(arguments), ANSI.RESET);
			return 0;
		});
		
		context.setTextrunHandler((ctx, text) -> {
			Matcher matcher = pattern.matcher(text);
			char code;
			String toReplace, group, replace = "";
			
			while(matcher.find()) {
				replace = "";
				
				toReplace = matcher.group(1);
				group = matcher.group(2);
				code = group.charAt(0);
				
				//System.out.println(toReplace + " " + group);
				
				switch(code) {
				case 'c': replace = ANSI.getColorFrom(group.charAt(1)).toString(); break;
				}
				text = text.replace(toReplace, replace);
			}
			
			System.out.printf("[%s] %s", ctx.getTyper(), text);
			if(context.isChoicing()) {
				System.out.println();
				return;
			}
			RuntimeContext.waitForInput();
		});
		
		// Genocide dialogue
		TSUSFunctions.isGeno = true;
		TSUSFunctions.setFlag("j_hm0", 2);
		System.out.println("Return value: " + runtime.eval(file.sceneByName("tem.ruin_exit")));
	
		/*
		for(DNXScene scene : file.getScenes()) {
			context.reset();
			System.out.println("Executing " + scene);
			System.out.println("Return value: " + runtime.eval(scene));
		}*/
	}
}

