package net.benjaminurquhart.diannex;

import java.util.HashMap;
import java.util.Map;

import net.benjaminurquhart.diannex.runtime.ANSI;
import net.benjaminurquhart.diannex.runtime.ExternalDNXFunction;
import net.benjaminurquhart.diannex.runtime.RuntimeContext;
import net.benjaminurquhart.diannex.runtime.Value;
import net.benjaminurquhart.diannex.runtime.ValueStack;

public class Main {
	
	public static void main(String[] args) throws Exception {
		
		DNXFile file = new DNXFile();
		DNXAssembler.assemble("pushbs \"\"", file).forEach(b -> System.out.println(b.toString(file)));
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



