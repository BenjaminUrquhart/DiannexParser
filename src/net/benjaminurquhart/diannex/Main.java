package net.benjaminurquhart.diannex;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.benjaminurquhart.diannex.DNXCompiler.CompileOutput;
import net.benjaminurquhart.diannex.runtime.*;

public class Main {
	
	public static boolean WAIT_FOR_INPUT = false;
	
	public static void main(String[] args) throws Exception {
		
		System.out.print(ANSI.RESET);
		
		CompileOutput out = DNXCompiler.compileFull("scene npc : talked(0, \"talkedToNpc\")\r\n" + 
				"{\r\n" + 
				"  sequence $talked\r\n" + 
				"  {\r\n" + 
				"    0: \"This is the line of dialogue for the first time talking\"\r\n" + 
				"    1: \"This is the line of dialogue for the second time talking\"\r\n" + 
				"  }\r\n" + 
				"  \"This line happens always, after either other line\"\r\n" + 
				"  if getFlag(\"beatGame\")\r\n" + 
				"    \"Congratulations on beating the game by the way\"\r\n" + 
				"}");
		
		DNXFile file = out.compileFile;
		DNXScene scene = out.scenes.get(0);
		
		//System.out.println(scene.disassemble(file));
		
		Pattern pattern = Pattern.compile("(`([^`]+)`)", Pattern.CASE_INSENSITIVE);
		
		DNXRuntime runtime = new DNXRuntime(file);
		RuntimeContext context = runtime.getContext();
		
		context.autodefineGlobals(true);
		context.registerExternalFunctions(ExternalFunction.getFrom(TSUSFunctions.class));
		
		context.setMissingExternalFunctionHandler((name, arguments) -> {
			System.out.printf("%sFunction stub: %s(args=%s)%s\n", ANSI.GRAY, name, Arrays.deepToString(arguments), ANSI.RESET);
			return 0;
		});
		
		context.setTextrunHandler((ctx, text) -> {
			text = text.replace("\\", "").replace("#", "\n");
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
			if(!text.startsWith("\n") && text.contains("\n")) {
				text = "\n" + text;
			}
			System.out.printf("[%s] %s", ctx.getTyper(), text);
			if(context.isChoicing()) {
				System.out.println();
				return;
			}
			if(WAIT_FOR_INPUT) {
				RuntimeContext.waitForInput();
			}
			else {
				System.out.println();
			}
		});
		
		System.out.println("Return value: " + runtime.eval(scene).get());
		System.out.println("Return value: " + runtime.eval(scene).get());
	}
	
	
	public static class TSUSFunctions {
		
		private static Map<String, Integer> flags = new HashMap<>(), persistFlags = new HashMap<>();
		
		public static boolean isGeno;
		
		@ExternalDNXFunction
		public static synchronized void setFlag(String flag, int val) {
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
		public static synchronized void setPersistFlag(String flag, int val) {
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
			try {
				System.out.printf("%sGoto: %s%s\n", ANSI.GRAY, scene, ANSI.RESET);
				Map<Integer, Value> oldLocalVars = context.localVars;
				ValueStack oldStack = context.stack;
				context.localVars = new HashMap<>();
				context.stack = new ValueStack();
				Value retVal =  context.runtime.eval(scene).get();
				context.localVars = oldLocalVars;
				context.stack = oldStack;
				return retVal;
			}
			catch(RuntimeException e) {
				throw e;
			}
			catch(Throwable e) {
				throw new RuntimeException(e);
			}
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

	
}



